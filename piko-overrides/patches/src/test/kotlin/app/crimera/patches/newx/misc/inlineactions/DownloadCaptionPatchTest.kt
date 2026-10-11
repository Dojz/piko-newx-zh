package app.crimera.patches.newx.misc.inlineactions

import app.crimera.bytecode.fieldReference
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.patcher.util.smali.toInstruction
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.builder.BuilderOffsetInstruction
import com.android.tools.smali.dexlib2.builder.MethodImplementationBuilder
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction21t
import com.android.tools.smali.dexlib2.builder.instruction.BuilderInstruction10t
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.NarrowLiteralInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.instruction.RegisterRangeInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.MethodReference
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodParameter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

/** Regression for Android 11 VerifyError after R8 reuses the translation presenter's p0. */
class DownloadCaptionPatchTest {
    @Test
    fun `all body returns preserve the entry receiver after object and primitive parameter reuse`() {
        for (registerCount in listOf(16, 48)) {
            val original = presenter(registerCount)
            val patched = original.withDownloadCaptionHooks(
                fieldReference("Lfixture/Presenter;->post:Ljava/lang/Object;"),
                fieldReference("Lfixture/Presenter;->state:Ljava/lang/Object;"),
            )
            assertEquals(original.implementation!!.registerCount, patched.body.implementation!!.registerCount)
            assertEquals(original.instructions.map { it.opcode }, patched.body.instructions.map { it.opcode })
            for (flag in listOf(0, 1)) {
                assertEquals("state", execute(original, flag, expectRecord = false))
                assertEquals("state", execute(patched.entry, flag, expectRecord = true, body = patched.body))
            }
        }
    }

    private fun presenter(registerCount: Int): MutableMethod {
        val receiver = registerCount - 3
        val flags = registerCount - 1
        val builder = MethodImplementationBuilder(registerCount)
        builder.addInstruction(BuilderInstruction21t(Opcode.IF_EQZ, flags, builder.getLabel("primitive")))
        builder.addInstruction("sget-object v3, Lfixture/State;->INSTANCE:Lfixture/State;".toInstruction())
        builder.addInstruction("sget-object v$receiver, Lfixture/Response;->INSTANCE:Lfixture/Response;".toInstruction())
        builder.addInstruction("return-object v3".toInstruction())
        builder.addLabel("primitive")
        builder.addInstruction("sget-object v3, Lfixture/State;->INSTANCE:Lfixture/State;".toInstruction())
        builder.addInstruction("const/16 v$receiver, 0x1".toInstruction())
        builder.addInstruction(BuilderInstruction10t(Opcode.GOTO, builder.getLabel("return")))
        builder.addLabel("return")
        builder.addInstruction("return-object v3".toInstruction())
        return MutableMethod(ImmutableMethod(
            "Lfixture/Presenter;", "present",
            listOf(
                ImmutableMethodParameter("Landroidx/compose/runtime/Composer;", emptySet(), null),
                ImmutableMethodParameter("I", emptySet(), null),
            ),
            "Ljava/lang/Object;", AccessFlags.PUBLIC.value, emptySet(), emptySet(),
            builder.methodImplementation,
        ))
    }

    // Execute the emitted instructions through both real control-flow paths. An iget against the
    // reused p0 fails here just as the device verifier rejects it, rather than merely matching text.
    private fun execute(method: MutableMethod, flag: Int, expectRecord: Boolean, body: MutableMethod? = null): Any? {
        val implementation = assertNotNull(method.implementation)
        val registers = arrayOfNulls<Any>(implementation.registerCount)
        registers[method.p0Register] = "presenter"
        registers[method.p0Register + 1] = "composer"
        registers[method.p0Register + 2] = flag
        val instructions = method.instructions.toList()
        var index = 0
        var recorded = 0
        var result: Any? = null
        repeat(100) {
            val instruction = instructions[index]
            fun a() = (instruction as OneRegisterInstruction).registerA
            fun b() = (instruction as TwoRegisterInstruction).registerB
            fun arguments(): List<Int> = if (instruction is RegisterRangeInstruction) {
                (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()
            } else {
                val invoke = instruction as FiveRegisterInstruction
                listOf(invoke.registerC, invoke.registerD, invoke.registerE, invoke.registerF, invoke.registerG)
                    .take(invoke.registerCount)
            }
            when (instruction.opcode) {
                Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16,
                Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16 -> registers[a()] = registers[b()]
                Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST ->
                    registers[a()] = (instruction as NarrowLiteralInstruction).narrowLiteral
                Opcode.SGET_OBJECT -> {
                    val field = (instruction as ReferenceInstruction).reference as FieldReference
                    registers[a()] = when (field.definingClass) {
                        "Lfixture/State;" -> "state"
                        "Lfixture/Response;" -> "response"
                        "Lfixture/Event;" -> "event"
                        else -> fail("Unexpected field $field")
                    }
                }
                Opcode.IPUT_OBJECT -> {
                    assertEquals("presenter", registers[b()], "State field must use the stable receiver")
                    assertEquals("state", registers[a()])
                }
                Opcode.IGET_OBJECT -> {
                    assertEquals("presenter", registers[b()], "Post field read must use the entry presenter")
                    registers[a()] = "post"
                }
                Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE -> {
                    val target = (instruction as ReferenceInstruction).reference as MethodReference
                    val called = assertNotNull(body)
                    assertEquals(called.name, target.name)
                    assertEquals(listOf("presenter", "composer", flag), arguments().map { registers[it] })
                    result = execute(called, flag, expectRecord = false)
                }
                Opcode.MOVE_RESULT_OBJECT -> registers[a()] = result
                Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE -> {
                    val target = (instruction as ReferenceInstruction).reference as MethodReference
                    assertEquals("record", target.name)
                    assertEquals(listOf("post", "state"), arguments().map { registers[it] })
                    recorded++
                }
                Opcode.IF_EQZ -> if (registers[a()] == 0) {
                    index = (instruction as BuilderOffsetInstruction).target.location.index
                    return@repeat
                }
                Opcode.GOTO -> {
                    index = (instruction as BuilderOffsetInstruction).target.location.index
                    return@repeat
                }
                Opcode.RETURN_OBJECT -> {
                    assertEquals(if (expectRecord) 1 else 0, recorded)
                    return registers[a()]
                }
                else -> fail("Unexpected opcode ${instruction.opcode}")
            }
            index++
        }
        fail("Fixture did not return")
    }
}
