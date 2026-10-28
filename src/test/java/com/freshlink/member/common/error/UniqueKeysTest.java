package com.freshlink.member.common.error;

import java.sql.SQLIntegrityConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;

class UniqueKeysTest {

    private DataIntegrityViolationException wrap(String mysqlMessage) {
        return new DataIntegrityViolationException("could not execute statement",
                new SQLIntegrityConstraintViolationException(mysqlMessage));
    }

    @Test
    void 원인_메시지의_키_이름으로_어느_UNIQUE가_충돌했는지_본다() {
        DataIntegrityViolationException nick = wrap("Duplicate entry 'fresh01' for key 'member.uk_member_nickname_normalized'");

        assertThat(UniqueKeys.isViolation(nick, UniqueKeys.MEMBER_NICKNAME)).isTrue();
        assertThat(UniqueKeys.isViolation(nick, UniqueKeys.STORE_NAME)).isFalse();
        assertThat(UniqueKeys.isViolation(wrap("Duplicate entry 'x' for key 'store.uk_store_name'"), UniqueKeys.STORE_NAME)).isTrue();
    }

    @Test
    void UNIQUE가_아닌_무결성_오류는_아니라고_본다() {
        assertThat(UniqueKeys.isViolation(wrap("Column 'name' cannot be null"), UniqueKeys.STORE_NAME)).isFalse();
        assertThat(UniqueKeys.isViolation(wrap("Duplicate entry 'x' for key 'store.PRIMARY'"), UniqueKeys.STORE_NAME)).isFalse();
    }
}
