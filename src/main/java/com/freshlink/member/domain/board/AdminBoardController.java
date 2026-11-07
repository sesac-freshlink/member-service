package com.freshlink.member.domain.board;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.common.web.AuthHeaders;
import com.freshlink.member.domain.admin.PageResponse;
import com.freshlink.member.domain.board.BoardResponses.FaqItem;
import com.freshlink.member.domain.board.BoardResponses.NoticeDetail;
import com.freshlink.member.domain.board.BoardResponses.NoticeSummary;
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

    @GetMapping("/notices")
    public PageResponse<NoticeSummary> notices(
            @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "0 이상이어야 합니다")
            @Max(value = BoardService.MAX_PAGE, message = "너무 큰 페이지입니다") int page) {
        requireAdmin(role);
        return boardService.allNotices(page);
    }

    @GetMapping("/notices/{noticeId}")
    public NoticeDetail notice(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                               @PathVariable Long noticeId) {
        requireAdmin(role);
        return boardService.anyNotice(noticeId);
    }

    @PostMapping("/notices")
    @ResponseStatus(HttpStatus.CREATED)
    public NoticeDetail createNotice(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                                     @Valid @RequestBody NoticeRequest request) {
        requireAdmin(role);
        return boardService.createNotice(request);
    }

    @PutMapping("/notices/{noticeId}")
    public NoticeDetail updateNotice(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                                     @PathVariable Long noticeId, @Valid @RequestBody NoticeRequest request) {
        requireAdmin(role);
        return boardService.updateNotice(noticeId, request);
    }

    @DeleteMapping("/notices/{noticeId}")
    public ResponseEntity<Void> deleteNotice(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                                             @PathVariable Long noticeId) {
        requireAdmin(role);
        boardService.deleteNotice(noticeId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/faqs")
    public List<FaqItem> faqs(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role) {
        requireAdmin(role);
        return boardService.allFaqs();
    }

    @PostMapping("/faqs")
    @ResponseStatus(HttpStatus.CREATED)
    public FaqItem createFaq(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                             @Valid @RequestBody FaqRequest request) {
        requireAdmin(role);
        return boardService.createFaq(request);
    }

    @PutMapping("/faqs/{faqId}")
    public FaqItem updateFaq(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role,
                             @PathVariable Long faqId, @Valid @RequestBody FaqRequest request) {
        requireAdmin(role);
        return boardService.updateFaq(faqId, request);
    }

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
