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

import java.util.Set;

import sofya.base.MethodSignature;

import org.apache.bcel.generic.ConstantPoolGen;
import org.apache.bcel.generic.MethodGen;
import org.apache.bcel.generic.InstructionList;
import org.apache.bcel.generic.InstructionHandle;
import org.apache.bcel.generic.CodeExceptionGen;

import gnu.trove.TIntObjectHashMap;

/*
 * Note: Changes to targeters (exception handlers, etc...) will be
 * undone at removal time: this requires the assumption that the
 * new target should always be the first instruction immediately
 * following the removed instrumentation. For reasons that should
 * be obvious, transformations that cause instructions to be inserted
 * after existing instructions will not update any targeters, and
 * are thus exempt from this consideration.
 */

/**
 * An instrumentor observer receives notifications from the
 * {@link sofya.ed.semantic.SemanticInstrumentor} indicating the
 * transformations made to instrumented classes.
 *
 * @author Alex Kinneer
 * @version 01/18/2007
 */
public interface InstrumentorObserver {
    /**
     * Notification that the instrumentor has started.
     */
    void begin();
    
    /**
     * Notification that the instrumentor has begun instrumenting a
     * new class.
     * 
     * <p><strong>Note:</strong> This method is called when the instrumentor
     * is not in adaptive instrumentation mode (it believes it is
     * instrumenting classes that were not previously instrumented). If the
     * instrumentor is in adaptive instrumentation mode, it will
     * issue the {@link #classBegin(ClassLog)} notification.</p>
     * 
     * @param className Name of the class being instrumented.
     */
    void classBegin(String className);

    /**
     * Notification that the instrumentor has begun instrumenting a
     * new class and that class was previously instrumented.
     * 
     * <p><strong>Note:</strong> This method is called when the instrumentor
     * is in adaptive instrumentation mode. If the instrumentor is not in
     * adaptive instrumentation mode, it will issue the
     * {@link #classBegin(String)} notification.</p>
     * 
     * @param classLog Instrumentation log for the class to be
     * reinstrumented, which records the instrumentation previously
     * applied to the class.
     */
    void classBegin(ClassLog classLog);

    /**
     * Notification that the instrumentor has begun instrumenting a
     * new method.
     * 
     * @param method The BCEL object used for modifying the method.
     * @param il Instruction list containing the bytecode for the method.
     */
    void methodBegin(MethodGen method, InstructionList il);

    /**
     * Requests an identifier for a new probe to be inserted by the
     * instrumentor. This identifier will be used to correlate subsequent
     * notifications.
     * 
     * @param eventCode Constant code indicating the type of event that
     * required insertion of the probe, as defined in
     * {@link sofya.ed.semantic.SemanticConstants}.
     * @param liveKeys Set of keys identifying the event specifications
     * that actively require the presence of the probe.
     * 
     * @return An integer to be used to correlate the changes made to the
     * bytecode to insert a new probe.
     */
    int newProbe(byte eventCode, Set<String> liveKeys);

    /**
     * Notification that bytecode was inserted to create a probe, or
     * some part of a logical probe (a probe that requires more than one
     * distinct bytecode modification to deliver a single event).
     * 
     * @param id Identifier associated with the probe, as obtained from
     * a prior call to {@link #newProbe(byte,Set)}.
     * @param eventCode Constant code indicating the type of event that
     * required insertion of the probe, as defined in
     * {@link sofya.ed.semantic.SemanticConstants}.
     * @param il Bytecode instructions that were inserted by the
     * instrumentor.
     * @param ih Handle to the instruction in the original bytecode
     * at which the insertion was performed.
     * @param precedes If <code>true</code>, the probe instructions were
     * inserted <em>before</em> <code>ih<code> in the instruction list,
     * otherwise they were inserted <em>after</em> <code>ih</code>.
     */
    void probeInserted(int id, byte eventCode, InstructionList il,
            InstructionHandle ih, boolean precedes);

    /**
     * Notification that a bytecode instruction was inserted to create a
     * probe, or some part of a logical probe (a probe that requires more
     * than one distinct bytecode modification to deliver a single event).
     * 
     * @param id Identifier associated with the probe, as obtained from
     * a prior call to {@link #newProbe(byte,Set)}.
     * @param eventCode Constant code indicating the type of event that
     * required insertion of the probe, as defined in
     * {@link sofya.ed.semantic.SemanticConstants}.
     * @param inserted Handle to the bytecode instruction that was
     * inserted.
     * @param ih Handle to the instruction in the original bytecode
     * at which the insertion was performed.
     * @param precedes If <code>true</code>, the new instruction was
     * inserted <em>before</em> <code>ih<code> in the instruction list,
     * otherwise it was inserted <em>after</em> <code>ih</code>.
     */
    void probeInserted(int id, byte eventCode, InstructionHandle inserted,
            InstructionHandle ih, boolean precedes);

    /**
     * Notification that a logical probe was removed.
     * 
     * <p>A logical probe constitutes the entirety of the bytecode changes
     * required to raise a single event through the
     * {@link sofya.ed.semantic.SemanticEventDispatcher}. Due to complexity
     * and performance considerations, individual notifications are not
     * issued for the removal of each constituent bytecode modification
     * (as is done during insertion).</p>
     * 
     * @param id Identifier associated with the probe, as obtained from
     * a call to {@link #newProbe(byte,Set)} at the time of instrumentation.
     * @param eventCode Constant code indicating the type of event that
     * depended on the existence of the probe, as defined in
     * {@link sofya.ed.semantic.SemanticConstants}.
     */
    void probeRemoved(int id, byte eventCode);

    /**
     * Notification that an exception handler was added to the exception
     * handler table for the method.
     * 
     * @param id Identifier associated with the probe, as obtained from
     * a prior call to {@link #newProbe(byte,Set)}.
     * @param handler The BCEL object representing the added exception
     * handler.
     * @param removable Flag indicating whether the added exception handler
     * is eligible for removal during adaptive reinstrumentation. Certain
     * exception handlers are added to implement transformations that
     * support the raising of multiple other events, and thus should not
     * be subsequently removed.
     */
    void exceptionHandlerAdded(int id, CodeExceptionGen handler,
            boolean removable);

    /**
     * Notification that the instrumentor has finished instrumenting a
     * method.
     * 
     * @param method The BCEL object used for modifying the method.
     * @param il Instruction list containing the (now instrumented)
     * bytecode for the method.
     * @param offsets A mapping from pre-instrumented bytecode
     * offsets to the BCEL <code>InstructionHandle</code> objects
     * referencing the instructions at those offsets. This may
     * facilitate working around an undesirable and poorly documented
     * behavior of BCEL (see also
     * {@link org.apache.bcel.generic.SUtility#getRealPosition}).
     */
    void methodEnd(MethodGen method, InstructionList il,
            TIntObjectHashMap offsets);

    /**
     * Notification that a call interceptor method was added to the class.
     * 
     * <p>A call interceptor method (also known as a trampoline) is a
     * synthetic method inserted by the instrumentor by request, to provide
     * access to method arguments at the site of calls to the target
     * method.</p>
     * 
     * @param id Identifier associated with the probe, as obtained from
     * a prior call to {@link #newProbe(byte,Set)}.
     * @param eventCode Constant code indicating the type of event that
     * required insertion of the probe, as defined in
     * {@link sofya.ed.semantic.SemanticConstants}.
     * @param call Handle to the invoke instruction that is being
     * replaced with an invocation of the interceptor method (the
     * interceptor method will call the original target method).
     * @param origOpcode Java bytecode opcode of the original invoke
     * instruction that is being replaced.
     * @param interceptor Signature of the added interceptor method.
     * @param target Signature of the target method (the method being
     * intercepted).
     */
    void callInterceptorAdded(int id, byte eventCode, InstructionHandle call,
            short origOpcode, MethodSignature interceptor,
            MethodSignature target);
    
    /**
     * Notification that a call to a field interceptor was
     * inserted in the class (see also,
     * {@link #fieldInterceptorMethodAdded(MethodSignature)}).
     * 
     * @param id Identifier associated with the probe, as obtained from
     * a prior call to {@link #newProbe(byte,Set)}.
     * @param eventCode Constant code indicating the type of event that
     * required insertion of the probe, as defined in
     * {@link sofya.ed.semantic.SemanticConstants}.
     * @param field_ih Handle to the field instruction that is being
     * replaced with an invocation of the interceptor method (the
     * interceptor method will execute the original field instruction).
     * @param cpg BCEL constant pool gen object for the class currently
     * being instrumented.
     */
    void fieldInterceptorAdded(int id, byte eventCode,
            InstructionHandle field_ih, ConstantPoolGen cpg);
    
    /**
     * Notification that a field interceptor method was added to the class.
     * 
     * <p>A field interceptor method (also known as a trampoline) is a
     * synthetic method inserted by the instrumentor by request, to
     * provide access to information about the current value (and new value,
     * in the case of write events) of a field when capturing field events
     * using breakpoints.</p>
     * 
     * @param interceptor Signature of the added interceptor method.
     */
    void fieldInterceptorMethodAdded(MethodSignature interceptor);

    /**
     * Notification that a static initializer was added to the class.
     * 
     * <p>A synthetic static initializer is added to classes that do not
     * already declare one. This is necessary to guarantee delivery
     * of certain events.</p>
     */
    void staticInitializerAdded();

    /**
     * Notification that bytecode transformations were applied to
     * provide for the raising of the method exit event.
     * 
     * <p>The ID passed to this method should be recorded and passed
     * back to the instrumentor during reinstrumentation, to prevent
     * the instrumentor from attempting to apply the exit probe
     * transformations again. This provides a convenient short-circuit
     * in the instrumentor logic, avoiding what would otherwise be
     * very complex bookkeeping to identify all the transformations
     * required to raise this event (exit probes must be inserted in
     * numerous places).</p>
     * 
     * @param id Identifier associated with the logical exit probe,
     * as obtained from a prior call to {@link #newProbe(byte,Set)}.
     */
    void exitProbeAdded(int id);

    /**
     * Notification that the instrumentor has finished instrumenting
     * a class.
     * 
     * @param className Name of the class that was instrumented.
     */
    void classEnd(String className);

    /**
     * Notification that the instrumentor has completed its work.
     */
    void end();
}
