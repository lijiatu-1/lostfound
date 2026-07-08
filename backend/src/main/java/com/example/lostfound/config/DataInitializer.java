package com.example.lostfound.config;

import com.example.lostfound.entity.*;
import com.example.lostfound.service.*;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final UserService userService;
    private final ItemService itemService;
    private final ApplicationService applicationService;
    private final MessageService messageService;
    private final CommentService commentService;

    public DataInitializer(UserService userService, ItemService itemService,
                           ApplicationService applicationService, MessageService messageService,
                           CommentService commentService) {
        this.userService = userService;
        this.itemService = itemService;
        this.applicationService = applicationService;
        this.messageService = messageService;
        this.commentService = commentService;
    }

    @Override
    public void run(String... args) {
        User existing = userService.findByOpenid("mock_openid_demo");
        if (existing != null) {
            return;
        }

        // 创建3个用户（user1 为管理员）
        User user1 = createUser("mock_openid_demo", "张同学", "authorized", "admin", "张三", "2023001001");
        User user2 = createUser("mock_openid_user2", "李同学", "authorized", "user", "李四", "2023001002");
        User user3 = createUser("mock_openid_user3", "王同学", "unauthorized", "user", null, null);

        // 创建6个失物招领
        LocalDateTime now = LocalDateTime.now();

        Item item1 = createItem(user1.getId(), "lost", "苹果AirPods Pro蓝牙耳机",
                "白色充电盒，左耳耳机丢失，在图书馆自习时发现不见。耳机是上周刚买的，充电盒上有轻微划痕。",
                "图书馆三楼自习室", "[\"耳机\",\"图书馆\",\"白色\"]", now.minusHours(2), "13800138001");
        item1.setCategory("电子产品");
        itemService.save(item1);

        Item item2 = createItem(user1.getId(), "lost", "华为Mate40手机",
                "黑色手机，带蓝色手机壳，屏幕右上角有轻微裂痕。",
                "一食堂二楼", "[\"手机\",\"食堂\",\"黑色\"]", now.minusHours(24), "13800138001");
        item2.setCategory("电子产品");
        itemService.save(item2);

        Item item3 = createItem(user2.getId(), "lost", "学生证",
                "计算机学院学生证，姓名李四。在教学楼A栋丢失。",
                "教学楼A栋", "[\"学生证\",\"教学楼\"]", now.minusDays(3), "13900139002");
        item3.setCategory("证件卡片");
        itemService.save(item3);

        Item item4 = createItem(user2.getId(), "found", "蓝色钱包",
                "在体育馆门口捡到蓝色钱包一个，内有现金和银行卡。",
                "体育馆门口", "[\"钱包\",\"体育馆\",\"蓝色\"]", now.minusHours(3), "13900139002");
        item4.setCategory("生活用品");
        itemService.save(item4);

        Item item5 = createItem(user1.getId(), "found", "小米充电宝",
                "白色充电宝，10000mAh，在图书馆一楼捡到。",
                "图书馆一楼", "[\"充电宝\",\"图书馆\",\"白色\"]", now.minusDays(1), "13800138001");
        item5.setCategory("电子产品");
        itemService.save(item5);

        Item item6 = createItem(user2.getId(), "found", "雨伞",
                "深蓝色长柄雨伞，在教学楼B栋一楼大厅捡到。",
                "教学楼B栋", "[\"雨伞\",\"教学楼\",\"蓝色\"]", now.minusDays(2), "13900139002");
        item6.setCategory("生活用品");
        itemService.save(item6);

        // 已过期的物品（测试发布者看到"延期7天"按钮）
        Item item7 = createItem(user1.getId(), "lost", "U盘",
                "银色金属U盘，64G，里面有课程设计资料。在机房丢失。",
                "计算机实验室", "[\"U盘\",\"机房\",\"银色\"]", now.minusDays(10), "13800138001");
        item7.setCategory("电子产品");
        item7.setStatus("expired");
        item7.setExpireAt(now.minusDays(3));
        itemService.save(item7);

        // 已解决的物品（测试发布者看到"延期7天"按钮）
        Item item8 = createItem(user2.getId(), "found", "篮球",
                "斯伯丁篮球，在篮球场捡到，已交给体育部。",
                "室外篮球场", "[\"篮球\",\"体育\"]", now.minusDays(5), "13900139002");
        item8.setCategory("其他物品");
        item8.setStatus("resolved");
        itemService.save(item8);

        // 没留手机号的招领帖（测试"发布者未留电话"提示）
        Item item9 = createItem(user2.getId(), "found", "水杯",
                "粉色保温杯，在教室最后一排捡到。",
                "教学楼C栋301", "[\"水杯\",\"教室\",\"粉色\"]", now.minusHours(5), null);
        item9.setCategory("生活用品");
        itemService.save(item9);

        // 创建演示评论（在寻物帖下）
        Comment c1 = new Comment();
        c1.setItemId(item1.getId());
        c1.setUserId(user2.getId());
        c1.setContent("我在图书馆三楼窗台上看到过一个类似的白色耳机盒，不知道是不是你的。");
        commentService.save(c1);

        Comment c2 = new Comment();
        c2.setItemId(item1.getId());
        c2.setUserId(user3.getId());
        c2.setContent("昨天下午好像有人在自习室捡到过耳机，可以问问管理员。");
        commentService.save(c2);

        // user1在user2的寻物帖下评论（测试发布者可删评论）
        Comment c3 = new Comment();
        c3.setItemId(item3.getId());
        c3.setUserId(user1.getId());
        c3.setContent("我在A栋一楼大厅的失物招领处看到过一张学生证，你可以去看看。");
        commentService.save(c3);

        // item7（已过期）下的评论
        Comment c4 = new Comment();
        c4.setItemId(item7.getId());
        c4.setUserId(user2.getId());
        c4.setContent("之前好像有同学捡到过一个U盘交给了机房老师。");
        commentService.save(c4);

        // 给user1创建多条演示消息
        messageService.sendSystemNotice(user1.getId(), "认证通过通知", "恭喜！你的校园卡认证已通过，现在可以发布失物信息了。");

        // 有人在寻物帖下留言的通知
        Message msg1 = new Message();
        msg1.setReceiverId(user1.getId());
        msg1.setType("comment_notice");
        msg1.setTitle("有人在你的寻物帖下留言");
        msg1.setContent("李同学在你发布的\"苹果AirPods Pro蓝牙耳机\"下留言：我在图书馆三楼窗台上看到过一个类似的白色耳机盒");
        msg1.setRelatedItemId(item1.getId());
        messageService.save(msg1);

        // 物品即将过期提醒
        Message msg2 = new Message();
        msg2.setReceiverId(user1.getId());
        msg2.setType("expire_notice");
        msg2.setTitle("物品即将过期");
        msg2.setContent("你发布的\"华为Mate40手机\"将在明天过期，请及时处理或延期。");
        msg2.setRelatedItemId(item2.getId());
        messageService.save(msg2);

        // 给user2也创建几条消息
        messageService.sendSystemNotice(user2.getId(), "认证通过通知", "恭喜！你的校园卡认证已通过，现在可以发布失物信息了。");

        Message msg3 = new Message();
        msg3.setReceiverId(user2.getId());
        msg3.setType("contact_notice");
        msg3.setTitle("有人想联系你");
        msg3.setContent("有同学看到了你发布的\"蓝色钱包\"，想要联系你取回物品。");
        msg3.setRelatedItemId(item4.getId());
        messageService.save(msg3);

        Message msg4 = new Message();
        msg4.setReceiverId(user2.getId());
        msg4.setType("comment_notice");
        msg4.setTitle("有人在你的寻物帖下留言");
        msg4.setContent("王同学在你发布的\"学生证\"下留言：我好像在教务处看到过这张学生证！");
        msg4.setRelatedItemId(item3.getId());
        messageService.save(msg4);

        // 给user3（未认证）也创建一条消息
        messageService.sendSystemNotice(user3.getId(), "欢迎使用失物招领平台", "完成校园卡认证后即可发布和评论失物信息。");
    }

    private User createUser(String openid, String nickname, String status, String role, String realName, String studentId) {
        User user = new User();
        user.setOpenid(openid);
        user.setNickname(nickname);
        user.setStatus(status);
        user.setRole(role);
        user.setRealName(realName);
        user.setStudentId(studentId);
        return userService.save(user);
    }

    private Item createItem(Long publisherId, String type, String title, String description,
                            String locationName, String tags, LocalDateTime createdAt, String phone) {
        Item item = new Item();
        item.setPublisherId(publisherId);
        item.setType(type);
        item.setTitle(title);
        item.setDescription(description);
        item.setLocationName(locationName);
        item.setTags(tags);
        item.setCreatedAt(createdAt);
        item.setExpireAt(createdAt.plusDays(7));
        item.setStatus("active");
        item.setImages("[]");
        item.setPhone(phone);
        return item;
    }
}
