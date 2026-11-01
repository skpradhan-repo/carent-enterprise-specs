package com.carent.auth.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carent.auth.TestSecrets;
import com.carent.auth.config.AuthProperties;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class UserDirectoryTest {

    private final String memberPassword = TestSecrets.password();
    private final String adjudicatorPassword = TestSecrets.password();

    private final UserDirectory directory = new UserDirectory(List.of(
            new AuthProperties.User("member1", memberPassword, List.of("ROLE_MEMBER")),
            new AuthProperties.User("adjudicator1", adjudicatorPassword,
                    List.of("ROLE_CLAIMS_ADJUDICATOR", "ROLE_MEMBER"))), new BCryptPasswordEncoder());

    @Test
    void correct_credentials_return_the_user_with_roles() {
        assertThat(directory.authenticate("adjudicator1", adjudicatorPassword)).hasValueSatisfying(user -> {
            assertThat(user.username()).isEqualTo("adjudicator1");
            assertThat(user.roles()).containsExactly("ROLE_CLAIMS_ADJUDICATOR", "ROLE_MEMBER");
        });
    }

    @Test
    void wrong_password_is_rejected() {
        assertThat(directory.authenticate("member1", TestSecrets.password())).isEmpty();
    }

    @Test
    void another_users_password_is_rejected() {
        assertThat(directory.authenticate("member1", adjudicatorPassword)).isEmpty();
    }

    @Test
    void unknown_user_is_rejected() {
        assertThat(directory.authenticate("nobody", memberPassword)).isEmpty();
    }

    @Test
    void username_is_case_sensitive() {
        assertThat(directory.authenticate("MEMBER1", memberPassword)).isEmpty();
    }

    @Test
    void null_or_empty_input_is_rejected_without_throwing() {
        assertThat(directory.authenticate(null, memberPassword)).isEmpty();
        assertThat(directory.authenticate("member1", null)).isEmpty();
        assertThat(directory.authenticate("", "")).isEmpty();
    }

    @Test
    void passwords_are_not_kept_in_clear_text() {
        assertThat(directory.toString()).doesNotContain(memberPassword).doesNotContain(adjudicatorPassword);
    }

    @Test
    void no_configured_users_means_nobody_can_log_in() {
        UserDirectory empty = new UserDirectory(List.of(), new BCryptPasswordEncoder());

        assertThat(empty.authenticate("member1", memberPassword)).isEmpty();
    }

    // ---- fail fast on bad configuration -------------------------------------------------------

    @Test
    void duplicate_usernames_are_rejected() {
        List<AuthProperties.User> users = List.of(
                new AuthProperties.User("member1", "pw-one", List.of("ROLE_MEMBER")),
                new AuthProperties.User("member1", "pw-two", List.of("ROLE_ADMIN")));

        assertThatThrownBy(() -> new UserDirectory(users, new BCryptPasswordEncoder()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void blank_username_or_password_is_rejected() {
        assertThatThrownBy(() -> new UserDirectory(
                List.of(new AuthProperties.User(" ", "pw", List.of("ROLE_MEMBER"))), new BCryptPasswordEncoder()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserDirectory(
                List.of(new AuthProperties.User("member1", "", List.of("ROLE_MEMBER"))),
                new BCryptPasswordEncoder())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void an_unresolved_placeholder_password_is_rejected_so_a_missing_env_var_cannot_become_a_password() {
        List<AuthProperties.User> users = List.of(
                new AuthProperties.User("member1", "${AUTH_MEMBER_PASSWORD}", List.of("ROLE_MEMBER")));

        assertThatThrownBy(() -> new UserDirectory(users, new BCryptPasswordEncoder()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("member1")
                .hasMessageNotContaining("${AUTH_MEMBER_PASSWORD}");
    }

    @Test
    void roles_must_be_present_and_known() {
        assertThatThrownBy(() -> new UserDirectory(
                List.of(new AuthProperties.User("member1", "pw", List.of())), new BCryptPasswordEncoder()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new UserDirectory(
                List.of(new AuthProperties.User("member1", "pw", List.of("ROLE_SUPERUSER"))),
                new BCryptPasswordEncoder())).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROLE_SUPERUSER");
    }

    @Test
    void all_three_spec_roles_are_accepted() {
        new UserDirectory(List.of(new AuthProperties.User("u", "pw",
                List.of("ROLE_MEMBER", "ROLE_CLAIMS_ADJUDICATOR", "ROLE_ADMIN"))), new BCryptPasswordEncoder());
    }
}
