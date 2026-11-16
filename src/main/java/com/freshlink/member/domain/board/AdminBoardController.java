package com.freshlink.member.domain.board;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.common.web.AuthHeaders;
import com.freshlink.member.domain.admin.PageResponse;
import com.freshlink.member.domain.board.BoardResponses.FaqItem;
import com.freshlink.member.domain.board.BoardResponses.NoticeDetail;
import com.freshlink.member.domain.board.BoardResponses.NoticeSummary;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 게시판 (추가 명세 A17, AD-04~AD-12). gateway가 /api/admin/** 를 ADMIN만 통과시키고 여기서도 한 번 더 본다 */
@RestController
@RequestMapping("/admin")
public class AdminBoardController {

    private final BoardService boardService;

    public AdminBoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    /** AD-04 공지 목록 (모든 대상, 고정 먼저 최신순 20개씩) */
    @Operation(summary = "AD-04 공지 목록 (모든 대상, 고정 먼저 최신순 20개씩)")
    @GetMapping("/notices")
    public PageResponse<NoticeSummary> notices(
            @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "0 이상이어야 합니다")
            @Max(value = BoardService.MAX_PAGE, message = "너무 큰 페이지입니다") int page) {
        requireAdmin(role);
        return boardService.allNotices(page);
    }

    /** AD-05 공지 하나를 본다 (모든 대상). 없으면 404 */
    @Operation(summary = "AD-05 공지 하나를 본다 (모든 대상). 없으면 404")
    @GetMapping("/notices/{noticeId}")
    public NoticeDetail notice(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                               @PathVariable Long noticeId) {
        requireAdmin(role);
        return boardService.anyNotice(noticeId);
    }

    /** AD-06 공지를 쓴다 (201) */
    @Operation(summary = "AD-06 공지를 쓴다 (201)")
    @PostMapping("/notices")
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeDetail createNotice(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                                     @Valid @RequestBody NoticeRequest request) {
        requireAdmin(role);
        return boardService.createNotice(request);
    }

    /** AD-07 공지의 모든 칸을 고친다. 없으면 404 */
    @Operation(summary = "AD-07 공지의 모든 칸을 고친다. 없으면 404")
    @PutMapping("/notices/{noticeId}")
    public NoticeDetail updateNotice(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                                     @PathVariable Long noticeId, @Valid @RequestBody NoticeRequest request) {
        requireAdmin(role);
        return boardService.updateNotice(noticeId, request);
    }

    /** AD-08 공지를 지운다 (204). 연결된 배너는 연결만 끊긴다 */
    @Operation(summary = "AD-08 공지를 지운다 (204). 연결된 배너는 연결만 끊긴다")
    @ApiResponse(responseCode = "204", description = "공지를 지웠다")
    @DeleteMapping("/notices/{noticeId}")
    public ResponseEntity<Void> deleteNotice(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                                             @PathVariable Long noticeId) {
        requireAdmin(role);
        boardService.deleteNotice(noticeId);
        return ResponseEntity.noContent().build();
    }

    /** AD-09 FAQ 목록 (모든 대상, 순서대로) */
    @Operation(summary = "AD-09 FAQ 목록 (모든 대상, 순서대로)")
    @GetMapping("/faqs")
    public List<FaqItem> faqs(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role) {
        requireAdmin(role);
        return boardService.allFaqs();
    }

    /** AD-10 FAQ를 쓴다 (201) */
    @Operation(summary = "AD-10 FAQ를 쓴다 (201)")
    @PostMapping("/faqs")
    @ResponseStatus(HttpStatus.CREATED)
    public FaqItem createFaq(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                             @Valid @RequestBody FaqRequest request) {
        requireAdmin(role);
        return boardService.createFaq(request);
    }

    /** AD-11 FAQ의 모든 칸을 고친다. 없으면 404 */
    @Operation(summary = "AD-11 FAQ의 모든 칸을 고친다. 없으면 404")
    @PutMapping("/faqs/{faqId}")
    public FaqItem updateFaq(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                             @PathVariable Long faqId, @Valid @RequestBody FaqRequest request) {
        requireAdmin(role);
        return boardService.updateFaq(faqId, request);
    }

    /** AD-12 FAQ를 지운다 (204) */
    @Operation(summary = "AD-12 FAQ를 지운다 (204)")
    @ApiResponse(responseCode = "204", description = "FAQ를 지웠다")
    @DeleteMapping("/faqs/{faqId}")
    public ResponseEntity<Void> deleteFaq(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                                          @PathVariable Long faqId) {
        requireAdmin(role);
        boardService.deleteFaq(faqId);
        return ResponseEntity.noContent().build();
    }

    private static void requireAdmin(String role) {
        if (!AuthHeaders.hasRole(role, AuthHeaders.ROLE_ADMIN)) {
            throw new BusinessException(MemberErrorCode.FORBIDDEN);
        }
    }
}
