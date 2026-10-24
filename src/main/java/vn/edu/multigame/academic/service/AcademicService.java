package vn.edu.multigame.academic.service;

import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.multigame.academic.dto.*;
import vn.edu.multigame.academic.entity.*;
import vn.edu.multigame.academic.repository.*;
import vn.edu.multigame.auth.service.AuthService;
import vn.edu.multigame.auth.service.AccountAccessPolicy;
import vn.edu.multigame.user.enums.UserRole;
import vn.edu.multigame.user.repository.UserRepository;

@Service @Profile("mysql") @Transactional(readOnly=true)
public class AcademicService {
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.context.ApplicationEventPublisher events;
    @org.springframework.beans.factory.annotation.Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    private final vn.edu.multigame.room.repository.ClassRoomStore classRooms;
    private final AcademicYearRepository years;
    private final SemesterRepository semesters;
    private final SubjectRepository subjects;
    private final SubjectOfferingRepository offerings;
    private final CourseClassRepository classes;
    private final TeachingAssignmentRepository assignments;
    private final UserRepository users;
    private final AuthService auth;
    private final AccountAccessPolicy accounts;
    public AcademicService(AcademicYearRepository years,SemesterRepository semesters,SubjectRepository subjects,
            SubjectOfferingRepository offerings,CourseClassRepository classes,TeachingAssignmentRepository assignments,
            UserRepository users,AuthService auth,AccountAccessPolicy accounts,vn.edu.multigame.room.repository.ClassRoomStore classRooms) {
        this.years=years; this.semesters=semesters; this.subjects=subjects; this.offerings=offerings;
        this.classes=classes; this.assignments=assignments; this.users=users; this.auth=auth; this.accounts=accounts;
        this.classRooms=classRooms;
    }
    private static <T> T found(Optional<T> result) {
        return result.orElseThrow(()->new AcademicFailure(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND"));
    }
    private static String code(String value) { return value.toUpperCase(Locale.ROOT); }
    public List<AcademicViews.Year> years(Authentication caller) {auth.requireAdmin(caller);return years.findAll(Sort.by("id")).stream().map(AcademicViews.Year::from).toList();}
    public List<AcademicViews.Semester> semesters(Authentication caller) {auth.requireAdmin(caller);return semesters.findAll(Sort.by("id")).stream().map(AcademicViews.Semester::from).toList();}
    public List<AcademicViews.Subject> subjects(Authentication caller) {auth.requireAdmin(caller);return subjects.findAll(Sort.by("id")).stream().map(AcademicViews.Subject::from).toList();}
    public List<AcademicViews.Offering> offerings(Authentication caller) {auth.requireAdmin(caller);return offerings.findAll(Sort.by("id")).stream().map(AcademicViews.Offering::from).toList();}
    public List<ClassView> classes(Authentication caller) {
        auth.requireAdmin(caller);return classes.findAll(Sort.by("id")).stream().map(this::view).toList();
    }
    public ClassView classDetail(Authentication caller,long id) {
        auth.requireAdmin(caller);return view(found(classes.findById(id)));
    }
    @Transactional public AcademicViews.Year createYear(Authentication caller,AcademicRequests.Year input) {
        auth.requireAdmin(caller);var year=new AcademicYear();year.setCode(code(input.code()));year.setName(input.name().strip());
        return AcademicViews.Year.from(years.saveAndFlush(year));
    }
    @Transactional public AcademicViews.Semester createSemester(Authentication caller,AcademicRequests.Semester input) {
        auth.requireAdmin(caller);found(years.findById(input.academicYearId()));
        var semester=new Semester();semester.setAcademicYearId(input.academicYearId());
        semester.setCode(code(input.code()));semester.setName(input.name().strip());return AcademicViews.Semester.from(semesters.saveAndFlush(semester));
    }
    @Transactional public AcademicViews.Subject createSubject(Authentication caller,AcademicRequests.Subject input) {
        auth.requireAdmin(caller);var subject=new Subject();subject.setCode(code(input.code()));subject.setName(input.name().strip());
        return AcademicViews.Subject.from(subjects.saveAndFlush(subject));
    }
    @Transactional public AcademicViews.Offering createOffering(Authentication caller,AcademicRequests.Offering input) {
        auth.requireAdmin(caller);found(semesters.findById(input.semesterId()));found(subjects.findById(input.subjectId()));
        var offering=new SubjectOffering();offering.setSemesterId(input.semesterId());offering.setSubjectId(input.subjectId());
        return AcademicViews.Offering.from(offerings.saveAndFlush(offering));
    }
    @Transactional public AcademicViews.Year updateYear(Authentication caller,long id,AcademicRequests.Year input) {
        auth.requireAdmin(caller);var year=found(years.findById(id));year.setCode(code(input.code()));year.setName(input.name().strip());
        return AcademicViews.Year.from(years.saveAndFlush(year));
    }
    @Transactional public AcademicViews.Semester updateSemester(Authentication caller,long id,AcademicRequests.Semester input) {
        auth.requireAdmin(caller);var semester=found(semesters.findById(id));
        if(!Objects.equals(semester.getAcademicYearId(),input.academicYearId()))throw new AcademicFailure(HttpStatus.CONFLICT,"PARENT_CHANGE_FORBIDDEN");
        semester.setCode(code(input.code()));semester.setName(input.name().strip());return AcademicViews.Semester.from(semesters.saveAndFlush(semester));
    }
    @Transactional public AcademicViews.Subject updateSubject(Authentication caller,long id,AcademicRequests.Subject input) {
        auth.requireAdmin(caller);var subject=found(subjects.findById(id));subject.setCode(code(input.code()));subject.setName(input.name().strip());
        return AcademicViews.Subject.from(subjects.saveAndFlush(subject));
    }
    @Transactional public void deleteYear(Authentication caller,long id) {auth.requireAdmin(caller);years.delete(found(years.findById(id)));years.flush();}
    @Transactional public void deleteSemester(Authentication caller,long id) {auth.requireAdmin(caller);semesters.delete(found(semesters.findById(id)));semesters.flush();}
    @Transactional public void deleteSubject(Authentication caller,long id) {auth.requireAdmin(caller);subjects.delete(found(subjects.findById(id)));subjects.flush();}
    @Transactional public ClassView createClass(Authentication caller,AcademicRequests.CourseClass input) {
        auth.requireAdmin(caller);found(offerings.findById(input.offeringId()));var course=new CourseClass();
        course.setOfferingId(input.offeringId());course.setCode(code(input.code()));course.setName(input.name().strip());
        course.setRequiredLessons(input.requiredLessons());classes.saveAndFlush(course);
        classRooms.create(course.getId());return view(course);
    }
    @Transactional(isolation=org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    public ClassView assign(Authentication caller,long classId,long teacherId) {
        auth.requireAdmin(caller);
        var teacher=found(users.findLockedById(teacherId));
        if(teacher.getDeletedAtMs()!=null || accounts.state(teacherId).banned(System.currentTimeMillis()))throw new AcademicFailure(HttpStatus.CONFLICT,"TEACHER_UNAVAILABLE");
        if(teacher.getRole()!=UserRole.GIANG_VIEN) throw new AcademicFailure(HttpStatus.CONFLICT,"TEACHER_REQUIRED");
        var course=found(classes.findById(classId));
        jdbc.queryForObject("SELECT id FROM course_class WHERE id=? FOR UPDATE",Long.class,classId);
        // User lock serializes repeated assignments, including assignments in different classes.
        if(!assignments.existsByCourseClassIdAndTeacherId(classId,teacherId)) {
            var assignment=new TeachingAssignment();assignment.setCourseClassId(classId);assignment.setTeacherId(teacherId);
            assignments.saveAndFlush(assignment);
        }
        events.publishEvent(new vn.edu.multigame.room.service.ClassStaffingChanged(classId));return view(course);
    }
    private long teacher(Authentication caller) {
        var user=auth.requireActiveUser(caller);
        if(user.getRole()!=UserRole.GIANG_VIEN) throw new AcademicFailure(HttpStatus.FORBIDDEN,"TEACHER_REQUIRED");
        return user.getId();
    }
    public List<ClassView> assignedClasses(Authentication caller) {
        return classes.findAssigned(teacher(caller)).stream().map(this::view).toList();
    }
    public ClassView assignedClass(Authentication caller,long classId) {
        long teacherId=teacher(caller);
        if(!assignments.existsByCourseClassIdAndTeacherId(classId,teacherId))
            throw new AcademicFailure(HttpStatus.FORBIDDEN,"CLASS_NOT_ASSIGNED");
        return view(found(classes.findById(classId)));
    }
    private ClassView view(CourseClass course) {
        var offering=found(offerings.findById(course.getOfferingId()));
        var semester=found(semesters.findById(offering.getSemesterId()));
        return new ClassView(course.getId(),offering.getId(),semester.getAcademicYearId(),semester.getId(),
                offering.getSubjectId(),course.getCode(),course.getName(),course.getRequiredLessons(),
                classRooms.teachers(course.getId()));
    }
}
