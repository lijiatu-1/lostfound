package com.example.lostfound;

import com.example.lostfound.entity.Item;
import com.example.lostfound.entity.User;
import com.example.lostfound.mapper.ItemMapper;
import com.example.lostfound.mapper.UserMapper;
import com.example.lostfound.service.AiUsageService;
import com.example.lostfound.util.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class WorkflowIntegrationTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", MYSQL::getJdbcUrl);
        properties.add("spring.datasource.username", MYSQL::getUsername);
        properties.add("spring.datasource.password", MYSQL::getPassword);
        properties.add("jwt.secret", () -> "test-only-signing-secret-at-least-thirty-two-chars");
        properties.add("wechat.appid", () -> "test-appid");
        properties.add("wechat.secret", () -> "");
        properties.add("app.public-api-base-url", () -> "https://api.example.test/api");
        properties.add("media.upload-dir", () -> "target/test-media");
    }

    @Autowired MockMvc mvc;
    @Autowired UserMapper users;
    @Autowired ItemMapper items;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtUtil jwt;
    @Autowired AiUsageService aiUsage;

    @Test
    void migrationAndApprovalChatClosure() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1", Integer.class))
                .isGreaterThanOrEqualTo(3);
        User publisher = user("authorized");
        User first = user("authorized");
        User second = user("authorized");
        Item item = item(publisher, "found");
        jdbc.update("UPDATE items SET phone = ?, location_lat = ?, location_lng = ? WHERE id = ?",
                "13800000000", 31.2, 121.5, item.getId());

        mvc.perform(post("/api/applications").header("Authorization", bearer(first))
                .contentType("application/json")
                .content("{\"itemId\":" + item.getId() + ",\"content\":\"有物品特征\",\"type\":\"help\"}"))
                .andExpect(status().isBadRequest());
        String firstBody = mvc.perform(post("/api/applications").header("Authorization", bearer(first))
                .contentType("application/json")
                .content("{\"itemId\":" + item.getId() + ",\"content\":\"有物品特征\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long firstId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(firstBody).path("application").path("id").asLong();
        mvc.perform(post("/api/applications").header("Authorization", bearer(second))
                .contentType("application/json")
                .content("{\"itemId\":" + item.getId() + ",\"content\":\"另一项特征\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/applications").header("Authorization", bearer(first))
                .contentType("application/json")
                .content("{\"itemId\":" + item.getId() + ",\"content\":\"重复\"}"))
                .andExpect(status().isBadRequest());

        String handled = mvc.perform(post("/api/applications/" + firstId + "/handle")
                .header("Authorization", bearer(publisher)).contentType("application/json")
                .content("{\"action\":\"accept\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversationId").isNumber())
                .andReturn().getResponse().getContentAsString();
        long conversationId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(handled)
                .path("conversationId").asLong();
        assertThat(jdbc.queryForObject("SELECT status FROM items WHERE id = ?", String.class, item.getId()))
                .isEqualTo("processing");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM applications WHERE item_id = ? AND status = 'closed'",
                Integer.class, item.getId())).isEqualTo(1);
        mvc.perform(get("/api/items/" + item.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.locationLat").doesNotExist())
                .andExpect(jsonPath("$.locationLng").doesNotExist());
        mvc.perform(post("/api/applications").header("Authorization", bearer(second))
                .contentType("application/json")
                .content("{\"itemId\":" + item.getId() + ",\"content\":\"再申请\"}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", bearer(first)).contentType("application/json")
                .content("{\"content\":\"我们在校门口见\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", bearer(second))).andExpect(status().isForbidden());
        mvc.perform(get("/api/messages/count").header("Authorization", bearer(publisher)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.conversationCount").value(1));
        mvc.perform(post("/api/items/" + item.getId() + "/resolve")
                .header("Authorization", bearer(publisher))).andExpect(status().isOk());
        mvc.perform(post("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", bearer(first)).contentType("application/json")
                .content("{\"content\":\"结案后消息\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/conversations/" + conversationId + "/messages")
                .header("Authorization", bearer(first))).andExpect(status().isOk());
    }

    @Test
    void productionStyleAuthAndLegacyFieldsAreRejected() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"code\":\"mock_openid_demo\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/items/my")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/conversations")).andExpect(status().isUnauthorized());
        User publisher = user("authorized");
        mvc.perform(post("/api/items").header("Authorization", bearer(publisher))
                .contentType("application/json")
                .content("{\"type\":\"lost\",\"title\":\"测试物品\","
                        + "\"description\":\"有明显划痕\",\"locationName\":\"北校区\","
                        + "\"phone\":\"13800000000\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/items").header("Authorization", bearer(publisher))
                .contentType("application/json")
                .content("{\"type\":\"lost\",\"title\":\"测试物品\","
                        + "\"description\":\"有明显划痕\",\"locationName\":\"北校区\","
                        + "\"locationLat\":31.2,\"locationLng\":121.5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void editPersistsManagedImagesAndTagsWithoutPublishingPhoneNumbers() throws Exception {
        User publisher = user("authorized");
        User stranger = user("authorized");
        Item item = item(publisher, "lost");
        jdbc.update("UPDATE items SET description = ? WHERE id = ?", "联系 13800000000", item.getId());
        mvc.perform(get("/api/items/" + item.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("联系 [已隐藏联系方式]"));
        mvc.perform(get("/api/items/search").param("keyword", "测试物品"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("13800000000"))));
        mvc.perform(post("/api/items").header("Authorization", bearer(publisher))
                .contentType("application/json")
                .content("{\"type\":\"lost\",\"title\":\"联系 13800000000\","
                        + "\"description\":\"蓝色\",\"locationName\":\"图书馆\"}"))
                .andExpect(status().isBadRequest());

        long firstAsset = uploadItemAsset(publisher);
        long secondAsset = uploadItemAsset(publisher);
        mvc.perform(put("/api/items/" + item.getId()).header("Authorization", bearer(publisher))
                .contentType("application/json")
                .content("{\"title\":\"蓝色水杯\",\"description\":\"杯盖有划痕\","
                        + "\"locationName\":\"北校区图书馆\",\"category\":\"生活用品\","
                        + "\"imageAssetIds\":[" + firstAsset + "," + secondAsset + "],"
                        + "\"tags\":[\"水杯\",\"蓝色\"]}"))
                .andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT images FROM items WHERE id = ?", String.class, item.getId()))
                .isEqualTo("[" + firstAsset + "," + secondAsset + "]");
        assertThat(jdbc.queryForObject("SELECT tags FROM items WHERE id = ?", String.class, item.getId()))
                .isEqualTo("[\"水杯\",\"蓝色\"]");
        mvc.perform(get("/api/items/" + item.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("蓝色水杯"));
        mvc.perform(put("/api/items/" + item.getId()).header("Authorization", bearer(publisher))
                .contentType("application/json")
                .content("{\"tags\":[\"13800000000\"]}"))
                .andExpect(status().isBadRequest());
        long strangerAsset = uploadItemAsset(stranger);
        mvc.perform(put("/api/items/" + item.getId()).header("Authorization", bearer(publisher))
                .contentType("application/json")
                .content("{\"imageAssetIds\":[" + strangerAsset + "]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reopenClosesOldConversationAndAllowsNewApplications() throws Exception {
        User publisher = user("authorized");
        User applicant = user("authorized");
        User another = user("authorized");
        Item item = item(publisher, "lost");
        String application = mvc.perform(post("/api/applications").header("Authorization", bearer(applicant))
                .contentType("application/json")
                .content("{\"itemId\":" + item.getId() + ",\"content\":\"我见过它\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.application.type").value("help"))
                .andReturn().getResponse().getContentAsString();
        long id = new com.fasterxml.jackson.databind.ObjectMapper().readTree(application)
                .path("application").path("id").asLong();
        mvc.perform(post("/api/applications/" + id + "/handle").header("Authorization", bearer(publisher))
                .contentType("application/json").content("{\"action\":\"accept\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/items/" + item.getId() + "/reopen")
                .header("Authorization", bearer(another))).andExpect(status().isForbidden());
        mvc.perform(post("/api/items/" + item.getId() + "/reopen")
                .header("Authorization", bearer(publisher))).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT status FROM conversations WHERE application_id = ?",
                String.class, id)).isEqualTo("closed");
        mvc.perform(post("/api/applications").header("Authorization", bearer(another))
                .contentType("application/json")
                .content("{\"itemId\":" + item.getId() + ",\"content\":\"新线索\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void privatePhotoAndAiBoundary() throws Exception {
        User owner = user("authorized");
        User stranger = user("authorized");
        User unverified = user("unauthorized");
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        mvc.perform(multipart("/api/assets").file(new MockMultipartFile(
                "file", "item.png", "image/png", png)).param("purpose", "item")
                .header("Authorization", bearer(unverified)))
                .andExpect(status().isForbidden());
        String uploaded = mvc.perform(multipart("/api/assets").file(new MockMultipartFile(
                "file", "card.png", "image/png", png)).param("purpose", "certification")
                .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long assetId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(uploaded).path("assetId").asLong();
        mvc.perform(get("/api/assets/" + assetId + "/content")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/assets/" + assetId + "/content").header("Authorization", bearer(stranger)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/assets/" + assetId + "/content").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        mvc.perform(post("/api/items").header("Authorization", bearer(owner))
                .contentType("application/json")
                .content("{\"type\":\"lost\",\"title\":\"书\",\"description\":\"蓝色\","
                        + "\"locationName\":\"图书馆\",\"imageAssetIds\":[" + assetId + "]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/recognize").header("Authorization", bearer(owner))
                .contentType("application/json").content("{\"imageUrl\":\"https://example.com/a.png\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/recognize").header("Authorization", bearer(stranger))
                .contentType("application/json").content("{\"assetId\":" + assetId + "}"))
                .andExpect(status().isBadRequest());
        String itemUpload = mvc.perform(multipart("/api/assets").file(new MockMultipartFile(
                "file", "item.png", "image/png", png)).param("purpose", "item")
                .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long itemAssetId = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(itemUpload).path("assetId").asLong();
        mvc.perform(post("/api/ai/recognize").header("Authorization", bearer(stranger))
                .contentType("application/json").content("{\"assetId\":" + itemAssetId + "}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/ai/recognize").header("Authorization", bearer(owner))
                .contentType("application/json").content("{\"assetId\":" + itemAssetId + "}"))
                .andExpect(status().isServiceUnavailable());
        for (int i = 0; i < 5; i++) assertThat(aiUsage.tryConsume(owner.getId())).isTrue();
        assertThat(aiUsage.tryConsume(owner.getId())).isFalse();
    }

    @Test
    void onlyOneConcurrentApprovalSucceeds() throws Exception {
        User publisher = user("authorized");
        User a = user("authorized");
        User b = user("authorized");
        Item item = item(publisher, "found");
        long first = submit(item, a);
        long second = submit(item, b);
        CountDownLatch start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var firstResult = pool.submit(() -> approve(first, publisher, start));
            var secondResult = pool.submit(() -> approve(second, publisher, start));
            start.countDown();
            int one = firstResult.get(30, TimeUnit.SECONDS);
            int two = secondResult.get(30, TimeUnit.SECONDS);
            assertThat(List.of(one, two)).containsExactlyInAnyOrder(200, 409);
        } finally {
            pool.shutdownNow();
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM conversations WHERE item_id = ?",
                Integer.class, item.getId())).isEqualTo(1);
    }

    private int approve(long id, User publisher, CountDownLatch start) throws Exception {
        start.await();
        return mvc.perform(post("/api/applications/" + id + "/handle")
                .header("Authorization", bearer(publisher)).contentType("application/json")
                .content("{\"action\":\"accept\"}")).andReturn().getResponse().getStatus();
    }

    private long submit(Item item, User applicant) throws Exception {
        String result = mvc.perform(post("/api/applications").header("Authorization", bearer(applicant))
                .contentType("application/json")
                .content("{\"itemId\":" + item.getId() + ",\"content\":\"我的物品\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(result).path("application").path("id").asLong();
    }

    private User user(String status) {
        User user = new User();
        user.setOpenid("test-" + UUID.randomUUID());
        user.setNickname("Test");
        user.setStatus(status);
        user.setRole("user");
        users.insert(user);
        return user;
    }

    private long uploadItemAsset(User owner) throws Exception {
        byte[] png = Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=");
        String uploaded = mvc.perform(multipart("/api/assets").file(new MockMultipartFile(
                "file", "item.png", "image/png", png)).param("purpose", "item")
                .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(uploaded).path("assetId").asLong();
    }

    private Item item(User publisher, String type) {
        Item item = new Item();
        item.setPublisherId(publisher.getId());
        item.setType(type);
        item.setTitle("测试物品");
        item.setDescription("特征");
        item.setLocationName("北校区图书馆");
        item.setImages("[]");
        item.setTags("[]");
        item.setStatus("active");
        item.setCreatedAt(LocalDateTime.now());
        item.setExpireAt(LocalDateTime.now().plusDays(7));
        items.insert(item);
        return item;
    }

    private String bearer(User user) { return "Bearer " + jwt.generateToken(user.getId()); }
}
