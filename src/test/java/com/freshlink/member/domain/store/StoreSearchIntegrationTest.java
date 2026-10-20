package com.freshlink.member.domain.store;

import com.freshlink.member.TestcontainersConfiguration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** ST-01 점포 검색을 실제 MySQL(Flyway V1)에서 확인한다. 테스트마다 다른 지역을 써서 데이터가 섞이지 않게 한다 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class StoreSearchIntegrationTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    JdbcTemplate jdbc;

    /** 점포 하나를 넣고 store_code를 S + id로 채운다 (등록 API는 AD-01에서 생긴다) */
    private long store(String name, String address, String region, String status) {
        String owner = "PENDING".equals(status) ? null : UUID.randomUUID().toString();
        jdbc.update("INSERT INTO store (name, address, region, status, owner_member_id, created_at, updated_at) "
                + "VALUES (?, ?, ?, ?, ?, NOW(6), NOW(6))", name, address, region, status, owner);
        Long id = jdbc.queryForObject("SELECT id FROM store WHERE name = ?", Long.class, name);
        jdbc.update("UPDATE store SET store_code = ? WHERE id = ?", "S" + id, id);
        return id;
    }

    @Test
    void 지역을_안_주면_서울의_ACTIVE_점포를_점포명순으로_내린다() throws Exception {
        long gangnam = store("프레시 강남점", "서울 강남구 테헤란로 1", "SEOUL", "ACTIVE");
        store("프레시 대기점", "서울 종로구 1", "SEOUL", "PENDING");
        store("프레시 중단점", "서울 중구 1", "SEOUL", "INACTIVE");
        store("프레시 부산점", "부산 해운대구 1", "BUSAN", "ACTIVE");
        long apgujeong = store("가나 압구정점", "서울 강남구 압구정로 2", "SEOUL", "ACTIVE");

        mockMvc.perform(get("/stores"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", contains("가나 압구정점", "프레시 강남점")))
                .andExpect(jsonPath("$[0].storeId").value(apgujeong))
                .andExpect(jsonPath("$[0].storeCode").value("S" + apgujeong))
                .andExpect(jsonPath("$[0].address").value("서울 강남구 압구정로 2"))
                .andExpect(jsonPath("$[0].region").value("SEOUL"))
                .andExpect(jsonPath("$[1].storeId").value(gangnam))
                .andExpect(jsonPath("$[0].status").doesNotExist())
                .andExpect(jsonPath("$[0].ownerMemberId").doesNotExist());
    }

    @Test
    void 검색어는_점포명이나_주소_일부와_맞으면_나온다() throws Exception {
        store("판교역점", "경기 성남시 분당구 판교역로 3", "GYEONGGI", "ACTIVE");
        store("수원점", "경기 수원시 팔달구 4", "GYEONGGI", "ACTIVE");
        store("분당 정자점", "경기 성남시 분당구 정자일로 5", "GYEONGGI", "ACTIVE");

        mockMvc.perform(get("/stores").param("region", "GYEONGGI").param("keyword", "판교"))
                .andExpect(jsonPath("$[*].name", contains("판교역점")));
        mockMvc.perform(get("/stores").param("region", "GYEONGGI").param("keyword", "분당구"))
                .andExpect(jsonPath("$[*].name", contains("분당 정자점", "판교역점")));
        mockMvc.perform(get("/stores").param("region", "GYEONGGI").param("keyword", "  수원  "))
                .andExpect(jsonPath("$[*].name", contains("수원점")));
    }

    @Test
    void 빈_검색어는_그_지역_전체() throws Exception {
        store("대전 둔산점", "대전 서구 둔산로 1", "DAEJEON", "ACTIVE");
        store("대전 유성점", "대전 유성구 2", "DAEJEON", "ACTIVE");

        mockMvc.perform(get("/stores").param("region", "DAEJEON").param("keyword", ""))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/stores").param("region", "DAEJEON").param("keyword", "   "))
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void 결과가_없으면_빈_배열() throws Exception {
        store("광주점", "광주 서구 1", "GWANGJU", "ACTIVE");

        mockMvc.perform(get("/stores").param("region", "GWANGJU").param("keyword", "없는가게"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/stores").param("region", "JEJU"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void 검색어의_퍼센트_밑줄은_글자_그대로_찾는다() throws Exception {
        store("울산 100%점", "울산 남구 1", "ULSAN", "ACTIVE");
        store("울산 1000점", "울산 북구 2", "ULSAN", "ACTIVE");
        store("울산_밑줄점", "울산 중구 3", "ULSAN", "ACTIVE");

        mockMvc.perform(get("/stores").param("region", "ULSAN").param("keyword", "100%"))
                .andExpect(jsonPath("$[*].name", contains("울산 100%점")));
        mockMvc.perform(get("/stores").param("region", "ULSAN").param("keyword", "_"))
                .andExpect(jsonPath("$[*].name", contains("울산_밑줄점")));
    }

    @Test
    void 없는_지역_코드나_너무_긴_검색어는_400() throws Exception {
        mockMvc.perform(get("/stores").param("region", "MARS"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/stores").param("keyword", "가".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
