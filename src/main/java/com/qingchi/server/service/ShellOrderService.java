package com.qingchi.server.service;

import com.qingchi.base.config.AppConfigConst;
import com.qingchi.base.common.ResultVO;
import com.qingchi.base.constant.ErrorCode;
import com.qingchi.base.constant.ShellOrderType;
import com.qingchi.base.model.user.ShellOrderDO;
import com.qingchi.base.model.user.UserContactDO;
import com.qingchi.base.model.user.UserDO;
import com.qingchi.base.model.user.UserDetailDO;
import com.qingchi.base.repository.shell.ShellOrderRepository;
import com.qingchi.base.repository.shell.UserContactRepository;
import com.qingchi.base.repository.user.UserDetailRepository;
import com.qingchi.base.repository.user.UserRepository;
import com.qingchi.base.store.ShellOrderStore;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.transaction.Transactional;
import java.util.Optional;

@Service
public class ShellOrderService {
    @Resource
    private UserDetailRepository userDetailRepository;

    @Resource
    private ShellOrderStore shellOrderStore;

    @Resource
    private ShellOrderRepository shellOrderRepository;

    @Resource
    private UserRepository userRepository;

    @Resource
    private UserContactRepository userContactRepository;

    @Transactional
    public ResultVO<String> saveShellOrders(UserDO user, UserDO beUser, Integer mineUserShell) {
        Integer expense = (Integer) AppConfigConst.appConfigMap.get(AppConfigConst.contactExpenseShellKey);

        Integer sysServiceReceiveRatio = (Integer) AppConfigConst.appConfigMap.get(AppConfigConst.sysServiceReceiveRatioKey);

        Integer sysTakeShell = expense * sysServiceReceiveRatio / 10;


        //用户获得的为 用户支付的减去系统佣金
        Integer userGiveShell = expense - sysTakeShell;


        //保存可观察
        UserContactDO userContactDO = new UserContactDO(user.getId(), beUser.getId());
        userContactDO = userContactRepository.save(userContactDO);

        //赠送用户
        ShellOrderDO shellOrderDO = new ShellOrderDO(user.getId(), -expense, ShellOrderType.expense, userContactDO.getId());
        //保存
        shellOrderDO = shellOrderRepository.save(shellOrderDO);

        //关联
        ShellOrderDO shellOrderGiveDO = new ShellOrderDO(beUser.getId(), userGiveShell, userContactDO.getId(), shellOrderDO.getId());
        //保存关联
        shellOrderGiveDO = shellOrderRepository.save(shellOrderGiveDO);


        shellOrderDO.setRelatedOrderId(shellOrderGiveDO.getId());
        //保存上一个的关联
        shellOrderDO = shellOrderRepository.save(shellOrderDO);

        //保存用户
        //用户消耗
        user.setShell(mineUserShell - expense);
        //保存用户消耗
        user = userRepository.save(user);

        beUser.setShell(beUser.getShell() + userGiveShell);
        //保存用户增加
        beUser = userRepository.save(beUser);

        Optional<UserDetailDO> userDetailDOOptional = userDetailRepository.findFirstOneByUserId(beUser.getId());
        if (userDetailDOOptional.isPresent()) {
            UserDetailDO userDetailDO = userDetailDOOptional.get();
            ResultVO<String> resultVO = new ResultVO<>();
            resultVO.setData(userDetailDO.getContactAccount());
            return resultVO;
        } else {
            return new ResultVO<>(ErrorCode.SYSTEM_ERROR);
        }
    }
}
