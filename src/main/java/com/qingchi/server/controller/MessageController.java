package com.qingchi.server.controller;

import com.qingchi.base.constant.status.ChatStatus;
import com.qingchi.base.constant.status.ChatUserStatus;
import com.qingchi.base.constant.status.ContentStatus;
import com.qingchi.base.constant.status.MessageReceiveStatus;
import com.qingchi.base.domain.ReportDomain;
import com.qingchi.base.model.chat.ChatDO;
import com.qingchi.base.repository.chat.ChatRepository;
import com.qingchi.base.model.chat.ChatUserDO;
import com.qingchi.base.repository.chat.ChatUserRepository;
import com.qingchi.base.common.ResultVO;
import com.qingchi.base.modelVO.MessageVO;
import com.qingchi.base.constant.*;
import com.qingchi.base.model.chat.MessageDO;
import com.qingchi.base.model.chat.MessageReceiveDO;
import com.qingchi.base.repository.chat.MessageReceiveRepository;
import com.qingchi.base.repository.chat.MessageRepository;
import com.qingchi.base.repository.notify.NotifyRepository;
import com.qingchi.base.service.NotifyService;
import com.qingchi.base.service.ReportService;
import com.qingchi.base.model.user.UserDO;
import com.qingchi.base.repository.user.UserRepository;
import com.qingchi.base.utils.QingLogger;
import com.qingchi.server.model.MessageAddVO;
import com.qingchi.server.model.MessageQueryVO;
import com.qingchi.server.model.MsgDeleteVO;
import com.qingchi.server.service.MessageService;
import com.qingchi.server.verify.ChatUserVerify;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.validation.Valid;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * @author qinkaiyuan
 * @date 2018-11-18 20:45
 */

@RestController
@RequestMapping("message")
public class MessageController {
    private final Logger log = LoggerFactory.getLogger(getClass());

    @Resource
    private UserRepository userRepository;
    @Resource
    private ChatRepository chatRepository;
    @Resource
    private MessageRepository messageRepository;
    @Resource
    private NotifyService notifyService;
    @Resource
    private NotifyRepository notifyRepository;
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Resource
    private ChatUserRepository chatUserRepository;
    @Resource
    private MessageReceiveRepository messageReceiveRepository;
    @Resource
    private ReportService reportService;
    @Resource
    private ReportDomain reportDomain;
    @Resource
    private ChatUserVerify chatUserVerify;
    @Resource
    private MessageService messageService;

    /**
     * toDO 这里有问题，都统一用的 msgid
     *
     * @param user
     * @param queryVO
     * @return
     */
    @PostMapping("queryMessages")
    public ResultVO<List<MessageVO>> queryMessages(UserDO user, @RequestBody @Valid MessageQueryVO queryVO) {
        List<MessageVO> messageVOS = new ArrayList<>();
        Long chatId = queryVO.getChatId();
        //msg id也要统一，举报的时候不知道是哪个
        List<Long> msgIds = queryVO.getMsgIds();
        //前台传入chatId，
        //校验chatId，对应的chatDO是否存在，
        //校验chat下是否存在此user


        //如果穿chatId，需要获取chatUserId，根据chatUserId获取msg

        //chatId操作，和user获取chatUser,判断是否存在

        //chatUserid，获取，判断user是否对应

        //查询所有messageReceive
        //前台没传这个值
        //什么状态的可以查询？chat的状态列表有哪些，各代表什么业务
        //chatUser呢


        //1,从chat页面进入，已有chat

        //首先获取chatId
        //无论是否登陆，都是先获取chatId
        if (chatId == null) {
            if (msgIds.size() < 1) {
                return new ResultVO<>(messageVOS);
            }
            Optional<MessageDO> messageDOOptional = messageRepository.findById(msgIds.get(0));
            if (!messageDOOptional.isPresent()) {
                return new ResultVO<>(messageVOS);
            }
            chatId = messageDOOptional.get().getChatId();
        }

        //用户为空，则判断chat是否为官方类型，如果是允许查询，返回数据
        Optional<ChatDO> chatDOOptional = chatRepository.findFirstByIdAndStatus(chatId, ChatStatus.enable);
        if (!chatDOOptional.isPresent()) {
            log.error("已删除的会话");
            return new ResultVO<>(ErrorCode.SYSTEM_ERROR);
        }
        ChatDO chatDO = chatDOOptional.get();
        //为群聊，
        if (chatDO.getType().equals(ChatType.system_group)) {
            Integer userId = null;
            if (user != null) {
                userId = user.getId();
            }
            //查询用户这个chatUser下的消息
            //已经确认过chat为可用的
            //则无论是否登陆都值查询chatId下的
            List<MessageDO> messageDOS = messageRepository.findTop30ByChatIdAndStatusAndIdNotInOrderByIdDesc(chatId, ChatStatus.enable, msgIds);
            messageVOS = MessageVO.messageDOToVOS(messageDOS, userId);
            //如果不为空，则判断用户是否有chat的权限
        } else {
            if (user == null) {
                log.error("无权限访问的会话");
                return new ResultVO<>(ErrorCode.SYSTEM_ERROR);
            }
            //如果用户没权限，则异常
            ResultVO<ChatUserDO> resultVO = chatUserVerify.checkChatHasUserId(chatId, user.getId());
            if (resultVO.hasError()) {
                return new ResultVO<>(resultVO);
            }
            ChatUserDO chatUserDO = resultVO.getData();
            List<MessageReceiveDO> messageDOS = messageReceiveRepository.findTop30ByChatUserIdAndChatUserStatusAndStatusAndMessageIdNotInOrderByIdDesc(chatUserDO.getId(), ChatUserStatus.enable, MessageReceiveStatus.enable, msgIds);
            messageVOS = MessageVO.messageReceiveDOToVOS(messageDOS);
        }
        return new ResultVO<>(messageVOS);
    }


    @PostMapping("sendMsg")
    public ResultVO<MessageVO> sendMsg(UserDO user, @RequestBody @Valid MessageAddVO msgAddVO) throws IOException {
        //只需要返回自己的
        return messageService.sendMsg(user, msgAddVO);
    }

    @PostMapping("deleteMsg")
    @ResponseBody
    public ResultVO<Object> deleteMsg(UserDO user, @RequestBody @Valid MsgDeleteVO msgVO) {
        /**
         * 删除动态操作，
         * 如果是系统管理员删除动态，则必须填写原因，删除后发表动态的用户将被封禁
         * 如果是自己删的自己的动态，则不需要填写原因，默认原因是用户自己删除
         */
        Optional<MessageDO> optionalMsgDO = messageRepository.findFirstOneByIdAndStatusIn(msgVO.getMsgId(), ContentStatus.otherCanSeeContentStatus);
        if (!optionalMsgDO.isPresent()) {
            return new ResultVO<>("无法删除不存在的消息");
        }
        MessageDO msgDO = optionalMsgDO.get();
        //如果是系统管理员操作,则把发表动态的用户封禁，如果用户本身也是管理员则不封禁，存在管理员自己删自己的情况
        if (UserType.system.equals(user.getType())) {
            //管理员不能删除内容了
            /*if (StringUtils.isEmpty(msgVO.getDeleteReason())) {
                return new ResultVO<>("必须填写删除原因");
            }
            //改为删除状态，和写上删除原因
            msgDO.setStatus(CommonStatus.delete);
            msgDO.setDeleteReason(msgVO.getDeleteReason());
            Date curDate = new Date();
            msgDO.setUpdateTime(curDate);
            UserDO violationUser = msgDO.getUser();
            //不为管理员自己删自己
            //且封禁撞他不为空，且为封禁，才执行封禁用户操作
            if (!ObjectUtils.isEmpty(msgVO.getViolation()) && msgVO.getViolation()) {
                violationUser.setStatus(CommonStatus.violation);
                msgDO.setStatus(CommonStatus.violation);
                //如果封禁的话，要改一下删除原因
                String deleteReason = "账号违规被封禁，请联系客服处理，删除原因：" + msgVO.getDeleteReason();
                //修改 D O的删除原因
                msgDO.setDeleteReason(deleteReason);
                violationUser.setViolationReason(msgVO.getDeleteReason());
                violationUser.setViolationCount(violationUser.getViolationCount() + 1);
                violationUser.setUpdateTime(curDate);
            }
            //给用户发送被封通知
            NotifyDO notifyDO = new NotifyDO(user, violationUser, NotifyType.delete_msg);
//            toDO notifyDO.setMessage(msgDO);
            notifyDO = notifyRepository.save(notifyDO);
            //推送消息
            notifyService.sendNotifies(Collections.singletonList(notifyDO));*/
            //不是管理员的话就必须是自己删除自己
        } else {
            //是否是自己删除自己的动态
            if (!msgDO.getUserId().equals(user.getId())) {
                QingLogger.logger.warn("有人尝试删除不属于自己的消息,用户名:{},id:{},尝试删除msgId：{}", user.getNickname(), user.getId(), msgDO.getId());
                return new ResultVO<>("系统异常，无法删除不属于自己的动态");
            }
            msgDO.setStatus(ContentStatus.delete);
            msgDO.setDeleteReason("用户自行删除");
        }
        msgDO.setUpdateTime(new Date());
        messageRepository.save(msgDO);
        return new ResultVO<>();
    }
}
