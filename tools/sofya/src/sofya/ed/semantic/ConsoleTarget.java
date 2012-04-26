/*
 * Copyright 2003-2007, Regents of the University of Nebraska
 *
 *  Licensed under the University of Nebraska Open Academic License,
 *  Version 1.0 (the "License"); you may not use this file except in
 *  compliance with the License. The License must be provided with
 *  the distribution of this software; if the license is absent from
 *  the distribution, please report immediately to galileo@cse.unl.edu
 *  and indicate where you obtained this software.
 *
 *  You may also obtain a copy of the License at:
 *
 *      http://sofya.unl.edu/LICENSE-1.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package sofya.ed.semantic;

/**
 * Pretty-prints observable trace events to the console
 * (<code>System.out</code>).
 *
 * @author Alex Kinneer
 * @version 08/15/2006
 */
public class ConsoleTarget implements EventListener {
    private StringBuilder sb = new StringBuilder();

    public void systemStarted() {
    }

    public void executionStarted() {
    }

    public void threadStartEvent(ThreadData td) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : started");
        System.out.println(sb.toString());
    }

    public void threadDeathEvent(ThreadData td) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : terminated");
        System.out.println(sb.toString());
    }

    public void classPrepareEvent(ThreadData td, String className) {
    }

    public void monitorContendEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : contending for monitor : ");
        sb.append("[oid=");
        sb.append(od.getId());
        sb.append("] ");
        System.out.println(sb.toString());
    }

    public void monitorAcquireEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : acquired monitor : ");
        sb.append("[oid=");
        sb.append(od.getId());
        sb.append("] ");
        System.out.println(sb.toString());
    }

    public void monitorPreReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : preparing to release monitor : ");
        sb.append("[oid=");
        sb.append(od.getId());
        sb.append("] ");
        System.out.println(sb.toString());
    }

    public void monitorReleaseEvent(ThreadData td, ObjectData od,
            MonitorData md) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : released monitor : ");
        sb.append("[oid=");
        sb.append(od.getId());
        sb.append("] ");
        System.out.println(sb.toString());
    }

    public void newAllocationEvent(ThreadData td, NewAllocationData nad) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : new ");
        sb.append(nad.getNewAllocationClass());
        System.out.println(sb.toString());
    }

    public void constructorCallEvent(ThreadData td, CallData cd) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : constructor call : ");
        sb.append(cd.getCalledSignature().toString());
        System.out.println(sb.toString());
    }

    public void constructorEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : construct object : ");
        sb.append("[oid=");
        sb.append(od.getId());
        sb.append("] ");
        sb.append(md.getSignature().toString());
        System.out.println(sb.toString());
    }

    public void constructorExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean exceptional) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : object constructed : ");
        sb.append("[oid=");
        sb.append(od.getId());
        sb.append("] ");
        sb.append(md.getSignature().toString());
        System.out.println(sb.toString());
    }

    public void staticFieldAccessEvent(ThreadData td, FieldData fd) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : access static field : ");
        sb.append(fd.getFullName());
        System.out.println(sb.toString());
    }

    public void instanceFieldAccessEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : access instance field : ");
        sb.append("[oid=");
        sb.append(od.getId());
        sb.append("] ");
        sb.append(fd.getFullName());
        System.out.println(sb.toString());
    }

    public void staticFieldWriteEvent(ThreadData td, FieldData fd) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : write static field : ");
        sb.append(fd.getFullName());
        System.out.println(sb.toString());
    }

    public void instanceFieldWriteEvent(
            ThreadData td, ObjectData od, FieldData fd) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : write instance field : ");
        sb.append("[oid=");
        sb.append(od.getId());
        sb.append("] ");
        sb.append(fd.getFullName());
        System.out.println(sb.toString());
    }

    public void staticCallEvent(ThreadData td, CallData cd) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : static call : ");
        sb.append(cd.getCalledSignature().toString());
        System.out.println(sb.toString());
    }

    public void virtualCallEvent(ThreadData td, CallData cd) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : virtual call : ");
        sb.append(cd.getCalledSignature().toString());
        System.out.println(sb.toString());
    }

    public void interfaceCallEvent(ThreadData td, CallData cd) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : interface call : ");
        sb.append(cd.getCalledSignature().toString());
        System.out.println(sb.toString());
    }

    public void callReturnEvent(ThreadData td, CallData cd,
            boolean exceptional) {
       sb.setLength(0);
       sb.append("[");
       sb.append(td.getId());
       sb.append("] \"");
       sb.append(td.getName());
       sb.append("\" : call return ");
       if (exceptional) sb.append("<exceptional> ");
       sb.append(": ");
       sb.append(cd.getCalledSignature().toString());
       System.out.println(sb.toString());
    }

    public void virtualMethodEnterEvent(ThreadData td, ObjectData od,
            MethodData md) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : enter virtual method : ");
        sb.append("[oid=");
        sb.append(od.getId());
        sb.append("] ");
        sb.append(md.getSignature().toString());
        System.out.println(sb.toString());
    }

    public void virtualMethodExitEvent(ThreadData td, ObjectData od,
            MethodData md, boolean isExceptional) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : exit virtual method : ");
        sb.append("[oid=");
        sb.append(od.getId());
        sb.append("] ");
        sb.append(md.getSignature().toString());
        System.out.println(sb.toString());
    }

    public void staticMethodEnterEvent(ThreadData td, MethodData md) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : enter static method : ");
        sb.append(md.getSignature().toString());
        System.out.println(sb.toString());
    }

    public void staticMethodExitEvent(ThreadData td, MethodData md, boolean exceptional) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : exit static method : ");
        sb.append(md.getSignature().toString());
        System.out.println(sb.toString());
    }

    public void exceptionThrowEvent(ThreadData td, ExceptionData ed) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : throw : ");
        sb.append(ed.getType());
        System.out.println(sb.toString());
    }

    public void exceptionCatchEvent(ThreadData td, ExceptionData ed) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : catch : ");
        sb.append(ed.getType());
        System.out.println(sb.toString());
    }

    public void staticInitializerEnterEvent(ThreadData td, MethodData md) {
        sb.setLength(0);
        sb.append("[");
        sb.append(td.getId());
        sb.append("] \"" );
        sb.append(td.getName());
        sb.append("\" : static initializer : ");
        sb.append(md.getSignature().getClassName());
        System.out.println(sb.toString());
    }

    public void systemExited() {
    }
}
