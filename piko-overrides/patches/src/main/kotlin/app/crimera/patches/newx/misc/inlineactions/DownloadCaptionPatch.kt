package app.crimera.patches.newx.misc.inlineactions

import app.crimera.bytecode.Target
import app.crimera.bytecode.insertHook
import app.crimera.bytecode.methodReference
import app.crimera.patches.newx.misc.extension.newXExtensionPatch
import app.crimera.patches.newx.models.fieldForToStringLabel
import app.crimera.patches.newx.models.requirePublicFields
import app.crimera.patches.newx.models.resolvedNewXPostModels
import app.crimera.patches.newx.models.newXPostModelResolutionPatch
import app.crimera.patches.newx.utils.requireExactlyOne
import app.crimera.patches.utils.scopedMatchAll
import app.morphe.patcher.Fingerprint
import app.morphe.patcher.Match
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableField.Companion.toMutable
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.cloneMutable
import app.morphe.util.p0Register
import app.morphe.util.numberOfParameterRegisters
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.immutable.ImmutableField
import com.android.tools.smali.dexlib2.immutable.ImmutableMethod
import com.android.tools.smali.dexlib2.immutable.ImmutableMethodImplementation
import com.android.tools.smali.dexlib2.immutable.instruction.ImmutableInstruction11x

private const val EXTENSION = "Lapp/morphe/extension/newx/misc/DownloadCaption;"
private const val OBJECT = "Ljava/lang/Object;"
private const val STRING = "Ljava/lang/String;"

context(_: BytecodePatchContext)
private fun anchor(scope: String, vararg labels: String): Match = requireExactlyOne(
    "NewX translation model ${labels.toList()}",
    Fingerprint(definingClass = scope, name = "toString", returnType = STRING,
        parameters = emptyList(), filters = labels.map { string(it) }).scopedMatchAll(),
)

context(context: BytecodePatchContext)
private fun expose(vararg fields: FieldReference) {
    fields.groupBy { it.definingClass }.forEach { (owner, members) ->
        context.mutableClassDefBy(owner).requirePublicFields(members)
    }
}

context(context: BytecodePatchContext)
private fun helper(name: String, locals: Int): MutableMethod {
    val owner = context.mutableClassDefBy(EXTENSION)
    val original = requireExactlyOne("NewX caption bridge $name", owner.methods.filter { it.name == name })
    val missing = locals + original.parameterTypes.size - (original.implementation?.registerCount ?: 0)
    if (missing <= 0) return original
    return original.cloneMutable(additionalRegisters = missing).also {
        owner.methods.remove(original)
        owner.methods.add(it)
    }
}

internal data class DownloadCaptionPresenterMethods(val entry: MutableMethod, val body: MutableMethod)

/** Run the untouched presenter in its own frame, then read its result with a stable receiver. */
internal fun MutableMethod.withDownloadCaptionHooks(
    postField: FieldReference,
    renderedStateField: FieldReference,
): DownloadCaptionPresenterMethods {
    if (implementation == null) throw PatchException("NewX translation presenter has no implementation")
    val body = cloneMutable(
        name = name + "\$pikoCaption",
        accessFlags = (accessFlags and (AccessFlags.PUBLIC.value or AccessFlags.PROTECTED.value).inv()) or
            AccessFlags.PRIVATE.value,
    )
    // A private body keeps all original registers, instructions, try ranges and branch targets.
    // The public entry has two locals plus its own incoming parameters; body register reuse
    // cannot affect this frame. No hooks or branch-label relocation are needed inside the body.
    val entry = MutableMethod(ImmutableMethod(
        definingClass, name, parameters, returnType, accessFlags, annotations, hiddenApiRestrictions,
        ImmutableMethodImplementation(numberOfParameterRegisters + 2,
            listOf(ImmutableInstruction11x(Opcode.RETURN_OBJECT, 1)), emptyList(), emptyList()),
    ))
    val arguments = mutableListOf(entry.p0Register)
    var parameter = entry.p0Register + 1
    parameterTypes.forEach { type ->
        arguments.add(parameter)
        parameter += if (type.toString() in listOf("J", "D")) 2 else 1
    }
    entry.insertHook(0, relocateBranchTargets = false) {
        invokeDirect(body, *arguments.toIntArray())
        moveResult(1, returnType)
        move(0, entry.p0Register, OBJECT)
        iput(1, 0, renderedStateField)
        iget(0, 0, postField)
        invokeStatic(methodReference("$EXTENSION->record($OBJECT$OBJECT)V"), 0, 1)
    }
    return DownloadCaptionPresenterMethods(entry, body)
}

internal val newXDownloadCaptionPatch = bytecodePatch {
    dependsOn(newXExtensionPatch, newXPostModelResolutionPatch)
    execute {
        val models = resolvedNewXPostModels()
        val postTypes = mutableClassDefBy(models.contextualPostDescriptor).interfaces.toSet() +
            models.contextualPostDescriptor
        val grok = anchor("Lcom/x/urt/items/post/translate/", "GrokTranslatePostState(shouldShowTranslation=")
        val grokState = grok.fieldForToStringLabel(", translationState=")
        val showTranslation = grok.fieldForToStringLabel("GrokTranslatePostState(shouldShowTranslation=")
        val completed = anchor("Lcom/x/groktranslate/", "Completed(content=")
        val content = completed.fieldForToStringLabel("Completed(content=")
        val translated = anchor("Lcom/x/groktranslate/", "TranslatedPost(text=")
        val translatedText = translated.fieldForToStringLabel("TranslatedPost(text=")
        val canTranslate = anchor("Lcom/x/urt/items/post/translate/", "CanTranslate(eventSink=")
        val standard = anchor("Lcom/x/urt/items/post/translate/", "Translated(translatePostResponse=")
        val standardResponse = standard.fieldForToStringLabel("Translated(translatePostResponse=")
        val response = anchor("Lcom/x/repositories/post/", "TranslatePostResponse(fromLanguage=")
        val responseText = response.fieldForToStringLabel(", translation=")
        if (translatedText.type != STRING || responseText.type != STRING ||
            showTranslation.type != "Z") {
            throw PatchException("NewX native translation contract changed")
        }
        expose(showTranslation, grokState, content, translatedText, standardResponse, responseText)

        helper("stateText", 2).apply {
            insertHook(0, relocateBranchTargets = false) {
                move(0, p0Register, OBJECT)
                instanceOf(1, 0, grok.originalMethod.definingClass)
                ifEqz(1, Target.Local("standard"))
                checkCast(0, grok.originalMethod.definingClass)
                iget(1, 0, showTranslation)
                ifEqz(1, Target.Local("none"))
                iget(0, 0, grokState)
                instanceOf(1, 0, completed.originalMethod.definingClass)
                ifEqz(1, Target.Local("none"))
                checkCast(0, completed.originalMethod.definingClass)
                iget(0, 0, content)
                instanceOf(1, 0, translated.originalMethod.definingClass)
                ifEqz(1, Target.Local("none"))
                checkCast(0, translated.originalMethod.definingClass)
                iget(0, 0, translatedText)
                returnObject(0)
                label("standard")
                instanceOf(1, 0, standard.originalMethod.definingClass)
                ifEqz(1, Target.Local("none"))
                checkCast(0, standard.originalMethod.definingClass)
                iget(0, 0, standardResponse)
                ifEqz(0, Target.Local("none"))
                checkCast(0, response.originalMethod.definingClass)
                iget(0, 0, responseText)
                returnObject(0)
                label("none")
                constInt(0, 0)
                returnObject(0)
            }
        }
        listOf(grok, canTranslate).forEach { model ->
            val stateClass = mutableClassDefBy(model.originalMethod.definingClass)
            val stateType = if (model == grok) stateClass.type else requireExactlyOne(
                "NewX standard translation state contract", stateClass.interfaces)
            val scope = if (model == grok) "Lcom/x/urt/items/post/translate/grok/" else "Lcom/x/urt/items/post/translate/"
            val presenters = Fingerprint(definingClass = scope, returnType = stateType,
                custom = { method, owner -> !AccessFlags.STATIC.isSet(method.accessFlags) &&
                    method.returnType == stateType &&
                    method.parameterTypes.any { it.toString() == "Landroidx/compose/runtime/Composer;" } &&
                    owner.fields.any { !AccessFlags.STATIC.isSet(it.accessFlags) &&
                        it.type in postTypes }
                }).scopedMatchAll()
            if (presenters.isEmpty()) throw PatchException("NewX $scope translation presenters missing")
            presenters.forEach { match ->
                val method = match.method
                val owner = mutableClassDefBy(method.definingClass)
                val postField = requireExactlyOne("NewX translation presenter post in ${owner.type}", owner.fields.filter {
                    !AccessFlags.STATIC.isSet(it.accessFlags) && it.type in postTypes
                })
                // Own the native state only for the existing presenter's lifetime. The global
                // observer is weak and stores no caption strings, so a GC cannot drop a visible state.
                val renderedStateField = ImmutableField(owner.type, "pikoDisplayedCaptionState", OBJECT,
                    AccessFlags.PRIVATE.value or AccessFlags.SYNTHETIC.value, null, emptySet(), emptySet()).toMutable()
                if (owner.fields.any { it.name == renderedStateField.name }) {
                    throw PatchException("NewX caption presenter state field already exists: ${owner.type}")
                }
                owner.fields.add(renderedStateField)
                val hooked = method.withDownloadCaptionHooks(postField, renderedStateField)
                if (owner.methods.any { it.name == hooked.body.name &&
                        it.parameterTypes == hooked.body.parameterTypes && it.returnType == hooked.body.returnType }) {
                    throw PatchException("NewX translation presenter body name already exists: ${hooked.body}")
                }
                owner.methods.remove(method)
                owner.methods.add(hooked.body)
                owner.methods.add(hooked.entry)
            }
        }
    }
}
