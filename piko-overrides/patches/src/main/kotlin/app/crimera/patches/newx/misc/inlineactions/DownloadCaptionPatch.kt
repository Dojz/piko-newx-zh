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
import app.morphe.patcher.extensions.InstructionExtensions.instructions
import app.morphe.patcher.patch.BytecodePatchContext
import app.morphe.patcher.patch.PatchException
import app.morphe.patcher.patch.bytecodePatch
import app.morphe.patcher.string
import app.morphe.patcher.util.proxy.mutableTypes.MutableMethod
import app.morphe.util.getReference
import app.morphe.util.cloneMutable
import app.morphe.util.p0Register
import com.android.tools.smali.dexlib2.AccessFlags
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.reference.FieldReference
import com.android.tools.smali.dexlib2.iface.reference.StringReference
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION = "Lapp/morphe/extension/newx/misc/DownloadCaption;"
private const val OBJECT = "Ljava/lang/Object;"
private const val STRING = "Ljava/lang/String;"
private const val FUNCTION = "Lkotlin/jvm/functions/Function1;"

context(_: BytecodePatchContext)
private fun anchor(scope: String, vararg labels: String): Match = requireExactlyOne(
    "NewX translation model ${labels.toList()}",
    Fingerprint(definingClass = scope, name = "toString", returnType = STRING,
        parameters = emptyList(), filters = labels.map { string(it) }).scopedMatchAll(),
)

context(context: BytecodePatchContext)
private fun instanceField(model: Match, type: String): FieldReference = requireExactlyOne(
    "NewX translation state field $type in ${model.originalMethod.definingClass}",
    context.mutableClassDefBy(model.originalMethod.definingClass).fields.filter {
        !AccessFlags.STATIC.isSet(it.accessFlags) && it.type == type
    },
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

internal val newXDownloadCaptionPatch = bytecodePatch {
    dependsOn(newXExtensionPatch, newXPostModelResolutionPatch)
    execute {
        val models = resolvedNewXPostModels()
        val canonical = anchor("Lcom/x/models/", "CanonicalPost(id=", ", grokAutoTranslation=")
        val canonicalInstructions = canonical.originalMethod.implementation!!.instructions.toList()
        val languageLabel = requireExactlyOne("NewX post language label", canonicalInstructions.indices.filter {
            canonicalInstructions[it].getReference<StringReference>()?.string == ", language="
        })
        val language = requireExactlyOne("NewX post language field", canonicalInstructions.drop(languageLabel + 1)
            .takeWhile { it.getReference<StringReference>() == null }
            .mapNotNull { it.getReference<FieldReference>() }
            .filter { it.definingClass == models.canonicalPostDescriptor && it.type == STRING }
            .distinctBy { it.toString() })
        val id = canonical.fieldForToStringLabel("CanonicalPost(id=")
        val idOwner = mutableClassDefBy(id.type)
        val identifier = requireExactlyOne("NewX post identifier", idOwner.fields.filter {
            !AccessFlags.STATIC.isSet(it.accessFlags) && it.type == STRING
        })
        val cached = canonical.fieldForToStringLabel(", grokAutoTranslation=")
        val available = anchor("Lcom/x/models/", "Available(translation=", ", pollTranslations=")
        val availableText = available.fieldForToStringLabel("Available(translation=")
        val grok = anchor("Lcom/x/urt/items/post/translate/", "GrokTranslatePostState(shouldShowTranslation=")
        val grokState = grok.fieldForToStringLabel(", translationState=")
        val grokSink = instanceField(grok, FUNCTION)
        val completed = anchor("Lcom/x/groktranslate/", "Completed(content=")
        val content = completed.fieldForToStringLabel("Completed(content=")
        val translated = anchor("Lcom/x/groktranslate/", "TranslatedPost(text=")
        val translatedText = translated.fieldForToStringLabel("TranslatedPost(text=")
        val canTranslate = anchor("Lcom/x/urt/items/post/translate/", "CanTranslate(eventSink=")
        val canSink = instanceField(canTranslate, FUNCTION)
        val standard = anchor("Lcom/x/urt/items/post/translate/", "Translated(translatePostResponse=")
        val standardResponse = standard.fieldForToStringLabel("Translated(translatePostResponse=")
        val standardSink = instanceField(standard, FUNCTION)
        val response = anchor("Lcom/x/repositories/post/", "TranslatePostResponse(fromLanguage=")
        val responseText = response.fieldForToStringLabel(", translation=")
        if (availableText.type != STRING || translatedText.type != STRING || responseText.type != STRING ||
            listOf(grokSink, canSink, standardSink).any { it.type != FUNCTION }) {
            throw PatchException("NewX native translation contract changed")
        }
        expose(language, id, identifier, cached, availableText, grokState, grokSink, content, translatedText,
            canSink, standardResponse, standardSink, responseText, models.contextualCanonicalPostField)

        helper("sourceLanguage", 2).apply {
            insertHook(0, relocateBranchTargets = false) {
                move(0, p0Register, OBJECT)
                instanceOf(1, 0, models.contextualPostDescriptor)
                ifEqz(1, Target.Local("canonical"))
                checkCast(0, models.contextualPostDescriptor)
                iget(0, 0, models.contextualCanonicalPostField)
                label("canonical")
                instanceOf(1, 0, models.canonicalPostDescriptor)
                ifEqz(1, Target.Local("none"))
                checkCast(0, models.canonicalPostDescriptor)
                iget(0, 0, language)
                returnObject(0)
                label("none")
                constInt(0, 0)
                returnObject(0)
            }
        }
        helper("postId", 2).apply {
            insertHook(0, relocateBranchTargets = false) {
                move(0, p0Register, OBJECT)
                instanceOf(1, 0, models.contextualPostDescriptor)
                ifEqz(1, Target.Local("canonical"))
                checkCast(0, models.contextualPostDescriptor)
                iget(0, 0, models.contextualCanonicalPostField)
                label("canonical")
                instanceOf(1, 0, models.canonicalPostDescriptor)
                ifEqz(1, Target.Local("none"))
                checkCast(0, models.canonicalPostDescriptor)
                iget(0, 0, id)
                ifEqz(0, Target.Local("none"))
                iget(0, 0, identifier)
                returnObject(0)
                label("none")
                constInt(0, 0)
                returnObject(0)
            }
        }
        helper("cachedText", 2).apply {
            insertHook(0, relocateBranchTargets = false) {
                val value = p0Register
                move(0, value, OBJECT)
                ifEqz(0, Target.Local("none"))
                instanceOf(1, 0, models.contextualPostDescriptor)
                ifEqz(1, Target.Local("canonical"))
                checkCast(0, models.contextualPostDescriptor)
                iget(0, 0, models.contextualCanonicalPostField)
                label("canonical")
                instanceOf(1, 0, models.canonicalPostDescriptor)
                ifEqz(1, Target.Local("none"))
                checkCast(0, models.canonicalPostDescriptor)
                iget(0, 0, cached)
                instanceOf(1, 0, available.originalMethod.definingClass)
                ifEqz(1, Target.Local("none"))
                checkCast(0, available.originalMethod.definingClass)
                iget(0, 0, availableText)
                returnObject(0)
                label("none")
                constInt(0, 0)
                returnObject(0)
            }
        }
        helper("stateText", 2).apply {
            insertHook(0, relocateBranchTargets = false) {
                move(0, p0Register, OBJECT)
                instanceOf(1, 0, grok.originalMethod.definingClass)
                ifEqz(1, Target.Local("standard"))
                checkCast(0, grok.originalMethod.definingClass)
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
        helper("eventSink", 2).apply {
            insertHook(0, relocateBranchTargets = false) {
                move(0, p0Register, OBJECT)
                listOf(grokSink, canSink, standardSink).forEachIndexed { index, field ->
                    instanceOf(1, 0, field.definingClass)
                    ifEqz(1, Target.Local("next$index"))
                    checkCast(0, field.definingClass)
                    iget(0, 0, field)
                    returnObject(0)
                    label("next$index")
                }
                constInt(0, 0)
                returnObject(0)
            }
        }
        helper("dispatch", 0).apply {
            insertHook(0, relocateBranchTargets = false) {
                checkCast(p0Register, FUNCTION)
                invokeInterface(methodReference("$FUNCTION->invoke($OBJECT)$OBJECT"), p0Register, p0Register + 1)
                returnVoid()
            }
        }

        val events = Fingerprint(definingClass = "Lcom/x/urt/items/post/translate/", name = "toString",
            returnType = STRING, parameters = emptyList(), filters = listOf(string("RequestTranslation"))).scopedMatchAll()
        if (events.size != 2) throw PatchException("NewX translation request events: ${events.size}")
        listOf(grok, canTranslate).forEach { model ->
            val stateClass = mutableClassDefBy(model.originalMethod.definingClass)
            val stateType = if (model == grok) stateClass.type else requireExactlyOne(
                "NewX standard translation state contract", stateClass.interfaces)
            val scope = if (model == grok) "Lcom/x/urt/items/post/translate/grok/" else "Lcom/x/urt/items/post/translate/"
            val event = requireExactlyOne("NewX $scope request event", events.filter {
                it.originalMethod.definingClass.startsWith("Lcom/x/urt/items/post/translate/grok/") == (model == grok)
            })
            val eventClass = mutableClassDefBy(event.originalMethod.definingClass)
            val eventField = requireExactlyOne("NewX translation request singleton", eventClass.fields.filter {
                AccessFlags.STATIC.isSet(it.accessFlags) && it.type == eventClass.type
            })
            expose(eventField)
            val presenters = Fingerprint(definingClass = scope, returnType = stateType,
                custom = { method, _ -> !AccessFlags.STATIC.isSet(method.accessFlags) &&
                    method.parameterTypes.any { it.toString() == "Landroidx/compose/runtime/Composer;" }
                }).scopedMatchAll()
            if (presenters.isEmpty()) throw PatchException("NewX $scope translation presenters missing")
            presenters.forEach { match ->
                val method = match.method
                val owner = mutableClassDefBy(method.definingClass)
                val postField = requireExactlyOne("NewX translation presenter post", owner.fields.filter {
                    !AccessFlags.STATIC.isSet(it.accessFlags) && it.type == models.contextualPostDescriptor
                })
                method.instructions.mapIndexedNotNull { index, instruction ->
                    index.takeIf { instruction.opcode == Opcode.RETURN_OBJECT }
                }.asReversed().forEach { index ->
                    val stateRegister = (method.instructions.elementAt(index) as OneRegisterInstruction).registerA
                    method.insertHook(index, excludedRegisters = listOf(stateRegister, method.p0Register), relocateBranchTargets = true) {
                        val post = scratchRegister()
                        val state = scratchRegister()
                        val request = scratchRegister()
                        move(state, stateRegister, OBJECT)
                        move(post, method.p0Register, OBJECT)
                        iget(post, post, postField)
                        sget(request, eventField)
                        invokeStatic(methodReference("$EXTENSION->record($OBJECT$OBJECT$OBJECT)V"), post, state, request)
                    }
                }
            }
        }
    }
}
