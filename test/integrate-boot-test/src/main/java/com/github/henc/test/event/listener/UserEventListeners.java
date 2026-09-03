package com.github.henc.test.event.listener;

import com.github.henc.integrateboot.event.AsyncEventListener;
import com.github.henc.test.event.UserCreated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Demo listeners for {@link UserCreated}, one per delivery contract the event module offers:
 * best-effort asynchronous reaction via {@link AsyncEventListener}, and a
 * transaction-bound listener that only runs once the creating transaction has committed.
 */
@Component
public class UserEventListeners {

    private static final Logger log = LoggerFactory.getLogger(UserEventListeners.class);

    private final List<String> asyncObserved = new CopyOnWriteArrayList<>();
    private final List<String> afterCommitObserved = new CopyOnWriteArrayList<>();

    @AsyncEventListener
    public void onUserCreatedAsync(UserCreated event) {
        asyncObserved.add(event.userName());
        log.info("Async listener observed UserCreated[{}]", event.userName());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUserCreatedCommitted(UserCreated event) {
        afterCommitObserved.add(event.userName());
        log.info("After-commit listener observed UserCreated[{}]", event.userName());
    }

    public List<String> getAsyncObserved() {
        return asyncObserved;
    }

    public List<String> getAfterCommitObserved() {
        return afterCommitObserved;
    }
}
