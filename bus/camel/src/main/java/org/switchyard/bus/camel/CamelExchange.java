/* 
 * JBoss, Home of Professional Open Source 
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved. 
 * See the copyright.txt in the distribution for a 
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use, 
 * modify, copy, or redistribute it subject to the terms and conditions 
 * of the GNU Lesser General Public License, v. 2.1. 
 * This program is distributed in the hope that it will be useful, but WITHOUT A 
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A 
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details. 
 * You should have received a copy of the GNU Lesser General Public License, 
 * v.2.1 along with this distribution; if not, write to the Free Software 
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, 
 * MA  02110-1301, USA.
 */
package org.switchyard.bus.camel;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import javax.xml.namespace.QName;

import org.switchyard.Context;
import org.switchyard.Exchange;
import org.switchyard.ExchangeHandler;
import org.switchyard.ExchangePattern;
import org.switchyard.ExchangePhase;
import org.switchyard.ExchangeState;
import org.switchyard.Message;
import org.switchyard.Scope;
import org.switchyard.Service;
import org.switchyard.ServiceDomain;
import org.switchyard.ServiceReference;
import org.switchyard.common.camel.SwitchYardCamelContext;
import org.switchyard.label.BehaviorLabel;
import org.switchyard.metadata.BaseExchangeContract;
import org.switchyard.metadata.ServiceOperation;
import org.switchyard.runtime.event.ExchangeCompletionEvent;
import org.switchyard.security.SecurityContext;
import org.switchyard.security.SecurityExchange;

/**
 * Exchange implementation which wraps Camel {@link org.apache.camel.Exchange} interface.
 */
public class CamelExchange implements SecurityExchange {

    private static final String DISPATCHER       = "org.switchyard.bus.camel.dispatcher";
    private static final String CONSUMER         = "org.switchyard.bus.camel.consumer";
    private static final String PROVIDER         = "org.switchyard.bus.camel.provider";
    private static final String CONTRACT         = "org.switchyard.bus.camel.contract";
    private static final String REPLY_HANDLER    = "org.switchyard.bus.camel.replyHandler";
    private static final String PHASE            = "org.switchyard.bus.camel.phase";
    private static final String FAULT            = "org.switchyard.bus.camel.fault";
    private static final String SECURITY_CONTEXT = "org.switchyard.bus.camel.securityContext";

    /**
     * Property/header name used to store property labels bag.
     */
    public static final String LABELS            = "org.switchyard.bus.camel.labels";

    /**
     * Header name used to mark message as sent.
     */
    public static final String MESSAGE_SENT      = "org.switchyard.bus.camel.messageSent";

    private org.apache.camel.Exchange _exchange;

    /**
     * Creates new CamelExchange.
     * 
     * @param dispatch Exchange dispatcher.
     * @param exchange Camel exchange instance.
     * @param replyHandler Reply handler.
     */
    public CamelExchange(ExchangeDispatcher dispatch, org.apache.camel.Exchange exchange, ExchangeHandler replyHandler) {
        _exchange = exchange;

        Map<String, Object> properties = exchange.getProperties();
        if (!properties.containsKey(DISPATCHER)) {
            _exchange.setProperty(DISPATCHER, dispatch);
        }
        if (!properties.containsKey(REPLY_HANDLER)) {
            _exchange.setProperty(REPLY_HANDLER, replyHandler);
        }
    }

    /**
     * Creates new CamelExchange.
     * 
     * This is some kind of "copy" constructor which allows to create {@link CamelExchange}
     * only from camel Exchange.
     * 
     * @param exchange Exchange instance.
     */
    public CamelExchange(org.apache.camel.Exchange exchange) {
        this(exchange.getProperty(DISPATCHER, ExchangeDispatcher.class), exchange, exchange.getProperty(REPLY_HANDLER, ExchangeHandler.class));
    }

    @Override
    public Context getContext() {
        return new CamelCompositeContext(_exchange, getMessage());
    }

    @Override
    public Context getContext(Message message) {
        if (_exchange.getIn() == message) {
            return getContext();
        }
        return message.getContext();
    }

    @Override
    public ServiceReference getConsumer() {
        return _exchange.getProperty(CONSUMER, ServiceReference.class);
    }

    @Override
    public Service getProvider() {
        return _exchange.getProperty(PROVIDER, Service.class);
    }

    @Override
    public BaseExchangeContract getContract() {
        if (_exchange.getProperty(CONTRACT) == null) {
            _exchange.setProperty(CONTRACT, new BaseExchangeContract());
        }
        return _exchange.getProperty(CONTRACT, BaseExchangeContract.class);
    }

    @Override
    public Exchange consumer(ServiceReference consumer, ServiceOperation operation) {
        _exchange.setProperty(CONSUMER, consumer);
        getContract().setConsumerOperation(operation);
        return this;
    }

    @Override
    public Exchange provider(Service provider, ServiceOperation operation) {
        if (getPhase() == ExchangePhase.OUT) {
            throw new IllegalStateException("Cannot change provider metadata after provider has been invoked!");
        }
        _exchange.setProperty(PROVIDER, provider);
        getContract().setProviderOperation(operation);
        return this;
    }

    @Override
    public CamelMessage getMessage() {
        return (CamelMessage) _exchange.getIn();
    }

    @Override
    public CamelMessage createMessage() {
        return new CamelMessage(_exchange);
    }

    @Override
    public void send(Message message) {
        org.apache.camel.Message camelMsg = extract(message);

        if (getPhase() == null) {
            _exchange.setProperty(PHASE, ExchangePhase.IN);
            _exchange.setIn(camelMsg);
            getContext().setProperty(Exchange.MESSAGE_ID, camelMsg.getMessageId());
        } else {
            _exchange.setProperty(PHASE, ExchangePhase.OUT);
            String id = getContext().getPropertyValue(MESSAGE_ID);
            _exchange.setIn(camelMsg);
            getContext().setProperty(Exchange.RELATES_TO, id);
            getContext().setProperty(Exchange.MESSAGE_ID, camelMsg.getMessageId());

        }

        initInContentType();
        sendInternal();
    }

    private org.apache.camel.Message extract(Message message) {
        if (message instanceof CamelMessage) {
            CamelMessage msg = (CamelMessage) message;
            if (msg.isSent()) {
                throw new IllegalArgumentException("Can not send same message twice. Use Message.copy() instead");
            }
            msg.sent(); // mark as sent
            return msg;
        }
        throw new IllegalArgumentException("CamelExchange accepts only CamelMessages");
    }

    @Override
    public void sendFault(Message message) {
        org.apache.camel.Message extract = extract(message);

        _exchange.setProperty(PHASE, ExchangePhase.OUT);
        _exchange.setIn(extract);
        _exchange.setProperty(FAULT, true);

        org.switchyard.Property rollbackOnFaultProperty = getContext().getProperty(org.switchyard.Exchange.ROLLBACK_ON_FAULT);
        if (rollbackOnFaultProperty == null || rollbackOnFaultProperty.getValue() == null) {
            getContext().setProperty(org.switchyard.Exchange.ROLLBACK_ON_FAULT, Boolean.FALSE, Scope.EXCHANGE);
        }
        
        sendInternal();
    }

    private void initInContentType() {
        QName exchangeInputType = getContract().getConsumerOperation().getInputType();

        if (exchangeInputType != null) {
            getContext().setProperty(Exchange.CONTENT_TYPE, exchangeInputType)
                .addLabels(BehaviorLabel.TRANSIENT.label());
        }
    }

    private void sendInternal() {
        _exchange.getProperty(DISPATCHER, ExchangeDispatcher.class).dispatch(this);
        if (isDone()) {
            ServiceDomain domain = ((SwitchYardCamelContext) _exchange.getContext()).getServiceDomain();
            long duration = System.nanoTime() - _exchange.getProperty(ExchangeCompletionEvent.EXCHANGE_DURATION + ".start", 0, Long.class);
            getContext().setProperty(ExchangeCompletionEvent.EXCHANGE_DURATION, TimeUnit.NANOSECONDS.toMillis(duration))
                .addLabels(BehaviorLabel.TRANSIENT.label());
            domain.getEventPublisher().publish(new ExchangeCompletionEvent(this));
        } else {
            _exchange.setProperty(ExchangeCompletionEvent.EXCHANGE_DURATION + ".start", System.nanoTime());
        }
    }

    @Override
    public ExchangeState getState() {
        return isFault(_exchange) ? ExchangeState.FAULT : ExchangeState.OK;
    }

    @Override
    public ExchangePhase getPhase() {
        return _exchange.getProperty(PHASE, ExchangePhase.class);
    }

    @Override
    public SecurityContext getSecurityContext() {
        if (!_exchange.getProperties().containsKey(SECURITY_CONTEXT)) {
            _exchange.setProperty(SECURITY_CONTEXT, new SecurityContext());
        }
        return _exchange.getProperty(SECURITY_CONTEXT, SecurityContext.class);
    }

    /**
     * Returns wrapped exchange.
     * @return Camel exchange.
     */
    public org.apache.camel.Exchange getExchange() {
        return _exchange;
    }

    @Override
    public ExchangeHandler getReplyHandler() {
        return _exchange.getProperty(REPLY_HANDLER, ExchangeHandler.class);
    }

    private boolean isDone() {
        ExchangePhase phase = getPhase();
        ExchangePattern mep = getContract().getConsumerOperation().getExchangePattern();
        return (ExchangePhase.IN.equals(phase) && ExchangePattern.IN_ONLY.equals(mep))
                || (ExchangePhase.OUT.equals(phase) && ExchangePattern.IN_OUT.equals(mep));
    }

    /**
     * Utility method which checks state of camel exchange used to implement
     * switchyard flow. 
     * 
     * @param exchange Camel exchange.
     * @return True if exchange state is fault.
     */
    public static boolean isFault(org.apache.camel.Exchange exchange) {
        return exchange.getProperty(FAULT, false, Boolean.class);
    }

}
