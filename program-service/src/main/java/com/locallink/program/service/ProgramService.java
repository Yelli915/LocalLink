package com.locallink.program.service;

import com.locallink.program.dto.ProgramDto;
import com.locallink.program.entity.Program;
import com.locallink.program.repository.ProgramRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProgramService {

    private final ProgramRepository programRepository;

    /**
     * 프로그램 등록 (지자체 담당자만 가능 - 역할 검증은 호출자인 ProgramController 가 UserServiceClient 로 수행)
     */
    @Transactional
    public ProgramDto.ProgramResponse createProgram(ProgramDto.CreateRequest request, Long officerId) {
        Program program = Program.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .category(request.getCategory())
                .price(request.getPrice())
                .officerId(officerId)
                .build();

        return ProgramDto.ProgramResponse.from(programRepository.save(program));
    }

    /**
     * 프로그램 단건 조회
     */
    public ProgramDto.ProgramResponse getProgram(Long id) {
        Program program = findProgramById(id);
        return ProgramDto.ProgramResponse.from(program);
    }

    /**
     * 전체 활성 프로그램 목록 조회
     */
    public List<ProgramDto.ProgramResponse> getAllPrograms() {
        return programRepository.findByStatus(Program.Status.ACTIVE).stream()
                .map(ProgramDto.ProgramResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 카테고리별 프로그램 조회
     */
    public List<ProgramDto.ProgramResponse> getProgramsByCategory(Program.Category category) {
        return programRepository.findByCategoryAndStatus(category, Program.Status.ACTIVE).stream()
                .map(ProgramDto.ProgramResponse::from)
                .collect(Collectors.toList());
    }

    /**
     * 프로그램 존재 여부 확인 (Participation Service → Program Service REST 호출용)
     */
    public boolean existsProgram(Long id) {
        return programRepository.existsById(id);
    }

    /**
     * 참여자 수 증가 (Participation Service 참여 활성화 시 호출)
     */
    @Transactional
    public void increaseParticipationCount(Long programId) {
        Program program = findProgramById(programId);
        program.increaseParticipationCount();
    }

    /**
     * 추천 서비스용: 카테고리별 미참여 프로그램 조회
     * - excludeProgramIds: 이미 참여한 프로그램 ID 목록
     */
    public List<ProgramDto.ProgramResponse> getRecommendPrograms(
            Program.Category category, List<Long> excludeProgramIds) {

        List<Program> programs = excludeProgramIds.isEmpty()
                ? programRepository.findByCategoryAndStatus(category, Program.Status.ACTIVE)
                : programRepository.findByCategoryAndStatusAndIdNotIn(
                        category, Program.Status.ACTIVE, excludeProgramIds);

        // 참여자 수 기준 내림차순 정렬
        return programs.stream()
                .sorted((a, b) -> b.getParticipationCount() - a.getParticipationCount())
                .map(ProgramDto.ProgramResponse::from)
                .collect(Collectors.toList());
    }

    private Program findProgramById(Long id) {
        return programRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("프로그램을 찾을 수 없습니다: " + id));
    }
}
