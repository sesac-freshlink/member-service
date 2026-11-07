package com.freshlink.member.domain.board;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.common.web.AuthHeaders;
import com.freshlink.member.domain.admin.PageResponse;
import com.freshlink.member.domain.board.BoardResponses.FaqItem;
import com.freshlink.member.domain.board.BoardResponses.NoticeDetail;
import com.freshlink.member.domain.board.BoardResponses.NoticeSummary;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 게시판 읽기 (추가 명세 A17, BD-01~03). 로그인한 고객·점주만. gateway 길 /api/members/**(로그인 필요)를 그대로 쓴다.
 * 고객은 ALL·CUSTOMER, 점주는 ALL·OWNER 글만 본다
 */
@RestController
@RequestMapping("/members")
public class BoardController {

    private final BoardService boardService;

    public BoardController(BoardService boardService) {
        this.boardService = boardService;
    }

    /** BD-01 공지 목록 (고정 먼저, 최신순 20개씩) */
    @GetMapping("/notices")
    public PageResponse<NoticeSummary> notices(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "0 이상이어야 합니다")
            @Max(value = BoardService.MAX_PAGE, message = "너무 큰 페이지입니다") int page,
            @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role) {
        return boardService.notices(isOwner(role), page);
    }

    /** BD-02 공지 상세 */
    @GetMapping("/notices/{noticeId}")
    public NoticeDetail notice(@PathVariable Long noticeId,
                               @RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role) {
        return boardService.notice(isOwner(role), noticeId);
    }

    /** BD-03 FAQ 목록 */
    @GetMapping("/faqs")
    public List<FaqItem> faqs(@RequestHeader(name = AuthHeaders.USER_ROLE, required = false) String role) {
        return boardService.faqs(isOwner(role));
    }

    /** 점주면 true, 고객이면 false, 둘 다 아니면 403 */
    private static boolean isOwner(String role) {
        if (AuthHeaders.hasRole(role, AuthHeaders.ROLE_OWNER)) {
            return true;
        }
        if (AuthHeaders.hasRole(role, AuthHeaders.ROLE_CUSTOMER)) {
            return false;
        }
        throw new BusinessException(MemberErrorCode.FORBIDDEN);
    }
}
