package tech.ymaatheus.certification_nlw.modules.students.useCases;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tech.ymaatheus.certification_nlw.modules.questions.entities.QuestionEntity;
import tech.ymaatheus.certification_nlw.modules.questions.repositories.QuestionRepository;
import tech.ymaatheus.certification_nlw.modules.students.dto.StudentCertificationAnswerDTO;
import tech.ymaatheus.certification_nlw.modules.students.dto.VerifyHasCertificationDTO;
import tech.ymaatheus.certification_nlw.modules.students.entities.AnswersCertificationsEntity;
import tech.ymaatheus.certification_nlw.modules.students.entities.CertificationStudentEntity;
import tech.ymaatheus.certification_nlw.modules.students.entities.StudentEntity;
import tech.ymaatheus.certification_nlw.modules.students.repositories.CertificationStudentRepository;
import tech.ymaatheus.certification_nlw.modules.students.repositories.StudentRepository;

@Service
public class StudentCertificationAnswersUseCase {

  @Autowired
  private StudentRepository studentRepository;

  @Autowired
  private QuestionRepository questionRepository;

  @Autowired
  private CertificationStudentRepository certificationStudentRepository;

  @Autowired
  private VerifyIfHasCertificationUseCase verifyIfHasCertificationUseCase;

  public CertificationStudentEntity execute(StudentCertificationAnswerDTO dto) throws Exception {
    var hasCertification = this.verifyIfHasCertificationUseCase.execute(new VerifyHasCertificationDTO(dto.getEmail(), dto.getTechnology()));

    if (hasCertification) {
      throw new Exception("Você já tirou sua certificação");
    }

    List<QuestionEntity> questions = questionRepository.findByTechnology(dto.getTechnology());
    List<AnswersCertificationsEntity> answersCertifications = new ArrayList<>();

    AtomicInteger correctAnswers = new AtomicInteger();

    dto.getQuestionsAnswers().stream()
        .forEach(questionAnswer -> {
          var question = questions.stream()
              .filter(q -> q.getId().equals(questionAnswer.getQuestionID()))
              .findFirst()
              .get();

          var findCorrectAlternative = question.getAlternatives().stream()
              .filter(alternative -> alternative.isCorrect())
              .findFirst()
              .get();

          if (findCorrectAlternative.getId().equals(questionAnswer.getAlternativeID())) {
            questionAnswer.setCorrect(true);
            correctAnswers.incrementAndGet();
          } else {
            questionAnswer.setCorrect(false);
          }

          var answersCertificationsEntity = AnswersCertificationsEntity.builder()
              .answerID(questionAnswer.getAlternativeID())
              .questionID(questionAnswer.getQuestionID())
              .isCorrect(questionAnswer.isCorrect())
              .build();

          answersCertifications.add(answersCertificationsEntity);
        });

    var student = studentRepository.findByEmail(dto.getEmail());
    UUID studentID;
    if (student.isEmpty()) {
      var studentCreated = StudentEntity.builder().email(dto.getEmail()).build();

      studentCreated = studentRepository.save(studentCreated);

      studentID = studentCreated.getId();
    } else {
      studentID = student.get().getId();
    }

    var certificationStudentEntity = CertificationStudentEntity
        .builder()
        .technology(dto.getTechnology())
        .studentID(studentID)
        .grate(correctAnswers.get())
        .build();

    var certificationStudentCreated = certificationStudentRepository.save(certificationStudentEntity);

    answersCertifications.stream().forEach(answersCertification -> {
      answersCertification.setCertificationID(certificationStudentEntity.getId());
      answersCertification.setCertificationStudentEntity(certificationStudentEntity);
    });

    certificationStudentEntity.setAnswersCertificationsEntities(answersCertifications);

    certificationStudentRepository.save(certificationStudentEntity);
    return certificationStudentCreated;
  }
}
