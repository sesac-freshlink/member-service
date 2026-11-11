package com.freshlink.member.domain.board;

import com.freshlink.common.error.BusinessException;
import com.freshlink.member.common.error.MemberErrorCode;
import com.freshlink.member.domain.board.BannerResponses.AdminBanner;
import com.freshlink.member.domain.board.BannerResponses.HomeBanner;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 메인 배너 (추가 명세 A18). 관리자가 쓰고·고치고·지우고·순서를 바꾼다. 로그인한 고객은 홈에 보일 배너만 읽는다.
 * 홈에 보일 배너: 보이기 켜짐 + 지금이 노출 기간 안(시작이 비면 바로, 끝이 비면 계속), 순서 → 먼저 만든 것, 최대 5장
 */
@Service
public class BannerService {

    /** 홈에 한 번에 보내는 최대 장수 (11/11 경우 결정 3) */
    static final int HOME_LIMIT = 5;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Logger log = LoggerFactory.getLogger(BannerService.class);

    private final BannerRepository bannerRepository;
    private final NoticeRepository noticeRepository;

    public BannerService(BannerRepository bannerRepository, NoticeRepository noticeRepository) {
        this.bannerRepository = bannerRepository;
        this.noticeRepository = noticeRepository;
    }

    /** BN-01 */
    @Transactional(readOnly = true)
    public List<HomeBanner> showing() {
        return bannerRepository.findShowing(OffsetDateTime.now(KST), Limit.of(HOME_LIMIT)).stream()
                .map(HomeBanner::from).toList();
    }

    /** AD-13 보이기·기간과 관계없이 모두, 순서대로 */
    @Transactional(readOnly = true)
    public List<AdminBanner> all() {
        return toAdmin(bannerRepository.findAllByOrderBySortOrderAscIdAsc());
    }

    /** AD-14 */
    @Transactional
    public AdminBanner create(BannerRequest req) {
        check(req);
        Banner b = save(Banner.create(req));
        log.info("배너 등록 bannerId={} noticeId={}", b.getId(), b.getNoticeId());
        return toAdmin(b);
    }

    /** AD-15 */
    @Transactional
    public AdminBanner update(Long id, BannerRequest req) {
        Banner b = bannerRepository.findForUpdate(id)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.BANNER_NOT_FOUND));
        check(req);
        b.update(req);
        return toAdmin(save(b));
    }

    /** AD-16 바로 지운다 */
    @Transactional
    public void delete(Long id) {
        Banner b = bannerRepository.findForUpdate(id)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.BANNER_NOT_FOUND));
        bannerRepository.delete(b);
        log.info("배너 삭제 bannerId={}", id);
    }

    /**
     * AD-17 지금 있는 배너 전부를 원하는 순서로 받아 앞에서부터 0, 1, 2… 로 다시 매긴다.
     * 빠지거나 겹치거나 없는 ID가 섞이면 400. 배너 전부를 잠가 순서 바꾸기 두 번이 섞이지 않게 한다
     */
    @Transactional
    public List<AdminBanner> reorder(List<Long> ids) {
        List<Banner> current = bannerRepository.findAllForUpdate();
        Set<Long> currentIds = current.stream().map(Banner::getId).collect(Collectors.toSet());
        if (ids.size() != current.size() || !new HashSet<>(ids).equals(currentIds)) {
            throw new BusinessException(MemberErrorCode.BANNER_ORDER_INVALID);
        }
        Map<Long, Banner> byId = current.stream().collect(Collectors.toMap(Banner::getId, Function.identity()));
        for (int i = 0; i < ids.size(); i++) {
            byId.get(ids.get(i)).moveTo(i);
        }
        bannerRepository.saveAllAndFlush(current);
        log.info("배너 순서 바꿈 count={}", ids.size());
        return all();
    }

    /** 기간 앞뒤, 연결할 공지가 있는지 */
    private void check(BannerRequest req) {
        if (!req.periodValid()) {
            throw new BusinessException(MemberErrorCode.BANNER_PERIOD_INVALID);
        }
        if (req.noticeId() != null && !noticeRepository.existsById(req.noticeId())) {
            throw new BusinessException(MemberErrorCode.NOTICE_NOT_FOUND);
        }
    }

    /** 확인한 뒤 그 공지가 같은 순간 지워지면 외래 키가 막는다. 그때도 공지가 없다고 답한다 */
    private Banner save(Banner b) {
        try {
            return bannerRepository.saveAndFlush(b);
        } catch (DataIntegrityViolationException e) {
            if (b.getNoticeId() == null) {
                throw e;
            }
            throw new BusinessException(MemberErrorCode.NOTICE_NOT_FOUND);
        }
    }

    private AdminBanner toAdmin(Banner b) {
        return toAdmin(List.of(b)).getFirst();
    }

    /** 연결한 공지 제목을 한 번에 읽어 붙인다 */
    private List<AdminBanner> toAdmin(List<Banner> banners) {
        Set<Long> noticeIds = banners.stream().map(Banner::getNoticeId).filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> titles = noticeRepository.findAllById(noticeIds).stream()
                .collect(Collectors.toMap(Notice::getId, Notice::getTitle));
        return banners.stream().map(b -> AdminBanner.from(b, titles.get(b.getNoticeId()))).toList();
    }
}
