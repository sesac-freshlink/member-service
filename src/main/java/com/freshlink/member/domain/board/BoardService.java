package com.freshlink.member.domain.board;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.domain.admin.PageResponse;
import com.freshlink.member.domain.board.BoardResponses.FaqItem;
import com.freshlink.member.domain.board.BoardResponses.NoticeDetail;
import com.freshlink.member.domain.board.BoardResponses.NoticeSummary;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 게시판 공지사항·FAQ (추가 명세 A17). 관리자가 쓰고 고치고 지운다. 로그인한 고객·점주는 자기 대상(ALL + 고객/점주) 글만 읽는다.
 * 공지 목록은 고정 글 먼저, 그다음 최신순 20개씩. FAQ는 순서(작을수록 위) → 먼저 쓴 순
 */
@Service
public class BoardService {

    static final int PAGE_SIZE = 20;
    /** 페이지 상한. 이보다 크면 DB 건너뛰기 수(page × 20)가 int를 넘어 500이 난다 */
    static final int MAX_PAGE = 100_000;
    private static final Sort NOTICE_ORDER = Sort.by(Sort.Order.desc("pinned"), Sort.Order.desc("createdAt"),
            Sort.Order.desc("id"));
    private static final Logger log = LoggerFactory.getLogger(BoardService.class);

    private final NoticeRepository noticeRepository;
    private final FaqRepository faqRepository;

    public BoardService(NoticeRepository noticeRepository, FaqRepository faqRepository) {
        this.noticeRepository = noticeRepository;
        this.faqRepository = faqRepository;
    }

    // ---------------------------------------------------------------- 읽기 (고객·점주)

    @Transactional(readOnly = true)
    public PageResponse<NoticeSummary> notices(boolean owner, int page) {
        return PageResponse.of(noticeRepository.findByAudienceIn(Audience.visibleTo(owner),
                PageRequest.of(page, PAGE_SIZE, NOTICE_ORDER)).map(NoticeSummary::from));
    }

    /** 없거나 내 대상이 아니면 404 */
    @Transactional(readOnly = true)
    public NoticeDetail notice(boolean owner, Long id) {
        Notice n = noticeRepository.findById(id)
                .filter(x -> Audience.visibleTo(owner).contains(x.getAudience()))
                .orElseThrow(() -> new BusinessException(MemberErrorCode.NOTICE_NOT_FOUND));
        return NoticeDetail.from(n);
    }

    @Transactional(readOnly = true)
    public List<FaqItem> faqs(boolean owner) {
        return faqRepository.findByAudienceInOrderBySortOrderAscIdAsc(Audience.visibleTo(owner)).stream()
                .map(FaqItem::from).toList();
    }

    // ---------------------------------------------------------------- 관리자

    @Transactional(readOnly = true)
    public PageResponse<NoticeSummary> allNotices(int page) {
        return PageResponse.of(noticeRepository.findAll(PageRequest.of(page, PAGE_SIZE, NOTICE_ORDER))
                .map(NoticeSummary::from));
    }

    @Transactional(readOnly = true)
    public NoticeDetail anyNotice(Long id) {
        return NoticeDetail.from(findNotice(id));
    }

    @Transactional
    public NoticeDetail createNotice(NoticeRequest req) {
        Notice n = noticeRepository.saveAndFlush(Notice.create(req.title(), req.content(), req.audience(),
                req.pinnedOrFalse()));
        log.info("공지 등록 noticeId={} audience={}", n.getId(), n.getAudience());
        return NoticeDetail.from(n);
    }

    @Transactional
    public NoticeDetail updateNotice(Long id, NoticeRequest req) {
        Notice n = findNotice(id);
        n.update(req.title(), req.content(), req.audience(), req.pinnedOrFalse());
        noticeRepository.saveAndFlush(n);
        return NoticeDetail.from(n);
    }

    @Transactional
    public void deleteNotice(Long id) {
        noticeRepository.delete(findNotice(id));
        log.info("공지 삭제 noticeId={}", id);
    }

    @Transactional(readOnly = true)
    public List<FaqItem> allFaqs() {
        return faqRepository.findAllByOrderBySortOrderAscIdAsc().stream().map(FaqItem::from).toList();
    }

    @Transactional
    public FaqItem createFaq(FaqRequest req) {
        Faq f = faqRepository.saveAndFlush(Faq.create(req.question(), req.answer(), req.audience(),
                req.sortOrderOrZero()));
        log.info("FAQ 등록 faqId={} audience={}", f.getId(), f.getAudience());
        return FaqItem.from(f);
    }

    @Transactional
    public FaqItem updateFaq(Long id, FaqRequest req) {
        Faq f = faqRepository.findById(id).orElseThrow(() -> new BusinessException(MemberErrorCode.FAQ_NOT_FOUND));
        f.update(req.question(), req.answer(), req.audience(), req.sortOrderOrZero());
        faqRepository.saveAndFlush(f);
        return FaqItem.from(f);
    }

    @Transactional
    public void deleteFaq(Long id) {
        Faq f = faqRepository.findById(id).orElseThrow(() -> new BusinessException(MemberErrorCode.FAQ_NOT_FOUND));
        faqRepository.delete(f);
        log.info("FAQ 삭제 faqId={}", id);
    }

    private Notice findNotice(Long id) {
        return noticeRepository.findById(id).orElseThrow(() -> new BusinessException(MemberErrorCode.NOTICE_NOT_FOUND));
    }
}
