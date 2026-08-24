package com.heraim.eco.service;

import com.heraim.eco.model.AuditContext;
import com.heraim.eco.model.AuditState;
import com.heraim.eco.model.Level;
import com.heraim.eco.model.RiskFlag;
import org.springframework.stereotype.Service;

@Service
public class AuditStateMachine {

    public AuditContext run (AuditContext context) {
        if (context.getState() == AuditState.INGEST){
            context.setState(AuditState.ANALYZE);
        }

        if (context.getState() == AuditState.ANALYZE) {
            RiskFlag flag = new RiskFlag(Level.HIGH, "High risk", "This is a high risk clause");
            context.getFlags().add(flag);
            if(context.isAwaitingHuman()){
                context.setState(AuditState.HUMAN_REVIEW);
                return context;
            }else{
                context.setState(AuditState.APPLY);
            }
        }
        return context;
    }

    public AuditContext resume(AuditContext context){
        if(context.isAwaitingHuman()) return context;

        if(context.getState() == AuditState.HUMAN_REVIEW){
            context.setState(AuditState.APPLY);
        }

        if(context.getState() == AuditState.APPLY){
            context.setState(AuditState.LOG);
        }

        if(context.getState() == AuditState.LOG){
            context.setState(AuditState.DONE);
        }
        return context;
    }
}

