package cn.itcast.account.service.impl;

import cn.itcast.account.entity.AccountFreeze;
import cn.itcast.account.mapper.AccountFreezeMapper;
import cn.itcast.account.mapper.AccountMapper;
import cn.itcast.account.service.AccountTCCService;
import io.seata.core.context.RootContext;
import io.seata.rm.tcc.api.BusinessActionContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * @author GF
 * @since 2023/5/18
 */
@Service
public class AccountTCCServiceImpl implements AccountTCCService {

    @Resource
    private AccountMapper accountMapper;

    @Resource
    private AccountFreezeMapper accountFreezeMapper;

    @Override
    @Transactional
    public void deduct(String userId, Integer money) {
        System.err.println("TCC - Try");
        String xid = RootContext.getXID();
        AccountFreeze check = accountFreezeMapper.selectById(xid);
        if (check != null) {
            //业务悬挂
            return;
        }

        accountMapper.deduct(userId, money);
        AccountFreeze accountFreeze = new AccountFreeze();
        accountFreeze.setFreezeMoney(money);
        accountFreeze.setUserId(userId);
        accountFreeze.setState(AccountFreeze.State.TRY);
        accountFreeze.setXid(xid);
        accountFreezeMapper.insert(accountFreeze);
    }

    @Override
    @Transactional
    public boolean confirm(BusinessActionContext context) {
        System.err.println("TCC - confirm");
        AccountFreeze accountFreeze = accountFreezeMapper.selectById(context.getXid());
        accountFreeze.setState(AccountFreeze.State.CONFIRM);
        accountFreezeMapper.updateById(accountFreeze);
        return true;
    }

    @Override
    @Transactional
    public boolean cancel(BusinessActionContext context) {
        System.err.println("TCC - cancel");
        String xid = context.getXid();
        AccountFreeze accountFreeze = accountFreezeMapper.selectById(xid);
        if (accountFreeze == null) {
            //空回滚
            System.err.println("TCC - cancel空回滚");
            accountFreeze = new AccountFreeze();
            accountFreeze.setFreezeMoney(0);
            accountFreeze.setUserId(context.getActionContext("userId").toString());
            accountFreeze.setState(AccountFreeze.State.ROLLBACK);
            accountFreeze.setXid(RootContext.getXID());
            accountFreezeMapper.insert(accountFreeze);
            return true;
        }

        if (accountFreeze.getState() == AccountFreeze.State.ROLLBACK || accountFreeze.getState() == AccountFreeze.State.CONFIRM) {
            //幂等
            System.err.println("幂等拦截");
            return true;
        }

        //恢复可用金额
        accountMapper.refund(accountFreeze.getUserId(), accountFreeze.getFreezeMoney());

        accountFreeze.setFreezeMoney(0);
        accountFreeze.setState(AccountFreeze.State.ROLLBACK);
        accountFreezeMapper.updateById(accountFreeze);

        return true;
    }

}
