package com.shardinglab.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.shardinglab.model.User;
import com.shardinglab.shard.ShardUnavailableException;
import com.shardinglab.shard.ShardedUser;
import com.shardinglab.shard.UserRepository;

@WebMvcTest(controllers = UserController.class)
class UserControllerTest {

    /** Own slice config so the real application class (and its shard properties scan) is not loaded. */
    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({UserController.class, ApiExceptionHandler.class})
    static class TestApp {
    }

    @Autowired
    MockMvc mvc;

    @MockitoBean
    UserRepository repository;

    private static ShardedUser stored(long id, int shard) {
        return new ShardedUser(shard, new User(id, "Ada", "ada@example.com", Instant.parse("2026-01-01T00:00:00Z")));
    }

    @Test
    void createReturns201WithLocationAndShard() throws Exception {
        given(repository.insert(any(User.class))).willReturn(stored(7, 1));

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":7,\"name\":\"Ada\",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/users/7"))
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.shardId").value(1));
    }

    @Test
    void createWithoutIdIs400() throws Exception {
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("id is required"));
        verifyNoInteractions(repository);
    }

    @Test
    void createWithBlankNameIs400() throws Exception {
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":7,\"name\":\"  \",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void createWithMalformedBodyIs400() throws Exception {
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content("{not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void duplicateIdIs409() throws Exception {
        given(repository.insert(any(User.class))).willThrow(new DuplicateKeyException("users_pkey detail"));

        MvcResult result = mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":7,\"name\":\"Ada\",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("users_pkey");
    }

    @Test
    void getReturnsUser() throws Exception {
        given(repository.findById(7)).willReturn(Optional.of(stored(7, 1)));

        mvc.perform(get("/users/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Ada"))
                .andExpect(jsonPath("$.shardId").value(1));
    }

    @Test
    void getMissingIs404() throws Exception {
        given(repository.findById(anyLong())).willReturn(Optional.empty());

        mvc.perform(get("/users/99")).andExpect(status().isNotFound());
    }

    @Test
    void getWithNonNumericIdIs400() throws Exception {
        mvc.perform(get("/users/abc")).andExpect(status().isBadRequest());
        verifyNoInteractions(repository);
    }

    @Test
    void updateReturnsUser() throws Exception {
        given(repository.update(7, "Ada", "ada@example.com")).willReturn(Optional.of(stored(7, 1)));

        mvc.perform(put("/users/7").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7));
    }

    @Test
    void updateMissingIs404() throws Exception {
        given(repository.update(anyLong(), anyString(), anyString())).willReturn(Optional.empty());

        mvc.perform(put("/users/99").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada\",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        given(repository.delete(7)).willReturn(true);

        mvc.perform(delete("/users/7")).andExpect(status().isNoContent());
    }

    @Test
    void deleteMissingIs404() throws Exception {
        given(repository.delete(99)).willReturn(false);

        mvc.perform(delete("/users/99")).andExpect(status().isNotFound());
    }

    @Test
    void shardDownIs503NamingTheShardWithoutLeakingDetails() throws Exception {
        given(repository.findById(5)).willThrow(new ShardUnavailableException(1,
                new CannotGetJdbcConnectionException("jdbc:postgresql://localhost:5434/db password=secret")));

        MvcResult result = mvc.perform(get("/users/5"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Shard 1 is unavailable"))
                .andReturn();
        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("jdbc:").doesNotContain("secret").doesNotContain("Exception");
    }

    @Test
    void unexpectedErrorIs500WithGenericMessage() throws Exception {
        given(repository.findById(5)).willThrow(new IllegalStateException("boom internal"));

        MvcResult result = mvc.perform(get("/users/5"))
                .andExpect(status().isInternalServerError())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("boom");
    }
}
