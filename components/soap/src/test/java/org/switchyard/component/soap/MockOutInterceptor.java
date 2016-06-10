package org.switchyard.component.soap;

import org.apache.cxf.binding.soap.SoapMessage;
import org.apache.cxf.binding.soap.interceptor.AbstractSoapInterceptor;
import org.apache.cxf.interceptor.Fault;
import org.apache.cxf.phase.Phase;

public class MockOutInterceptor extends AbstractSoapInterceptor {

    private static int _callCount = 0;
    private static String _id;
    private static String _phase;
    
    public MockOutInterceptor() {
        super(Phase.SEND);
        _phase = Phase.SEND;
    }
    
    public MockOutInterceptor(String p) {
        super(p);
        _phase = p;
    }
    
    public MockOutInterceptor(String i, String p) {
        super(i,p);
        _id = i;
        _phase = p;
    }
    
    @Override
    public void handleMessage(SoapMessage message) throws Fault {
        _callCount++;
    }
    
    public static int getCallCount() {
        return _callCount;
    }
    
    public static String getInterceptorID() {
        return _id;
    }
    
    public static String getInterceptorPhase() {
        return _phase;
    }
    
    public static void reset() {
        _callCount = 0;
    }

}
