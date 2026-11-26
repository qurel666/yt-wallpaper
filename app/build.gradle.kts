import com.android.build.api.instrumentation.AsmClassVisitorFactory
import com.android.build.api.instrumentation.ClassContext
import com.android.build.api.instrumentation.ClassData
import com.android.build.api.instrumentation.InstrumentationParameters
import com.android.build.api.instrumentation.InstrumentationScope
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

abstract class Android9CompatFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {
    override fun createClassVisitor(classContext: ClassContext, nextClassVisitor: ClassVisitor): ClassVisitor {
        return object : ClassVisitor(Opcodes.ASM9, nextClassVisitor) {
            override fun visitMethod(
                access: Int,
                name: String?,
                descriptor: String?,
                signature: String?,
                exceptions: Array<out String>?
            ): MethodVisitor {
                val mv = super.visitMethod(access, name, descriptor, signature, exceptions)
                return object : MethodVisitor(Opcodes.ASM9, mv) {
                    override fun visitMethodInsn(
                        opcode: Int,
                        owner: String?,
                        mName: String?,
                        mDesc: String?,
                        isInterface: Boolean
                    ) {
                        val helper = "com/tv/ytwallpaper/CompatHelper"
                        when {
                            opcode == Opcodes.INVOKESTATIC && owner == "java/net/URLDecoder" && mName == "decode" &&
                                mDesc == "(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "urlDecode", mDesc, false)

                            opcode == Opcodes.INVOKESTATIC && owner == "java/net/URLEncoder" && mName == "encode" &&
                                mDesc == "(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "urlEncode", mDesc, false)

                            opcode == Opcodes.INVOKEVIRTUAL && owner == "java/io/InputStream" && mName == "readAllBytes" &&
                                mDesc == "()[B" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "readAllBytes", "(Ljava/io/InputStream;)[B", false)

                            opcode == Opcodes.INVOKEVIRTUAL && owner == "java/io/InputStream" && mName == "transferTo" &&
                                mDesc == "(Ljava/io/OutputStream;)J" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "transferTo", "(Ljava/io/InputStream;Ljava/io/OutputStream;)J", false)

                            opcode == Opcodes.INVOKEVIRTUAL && owner == "java/io/ByteArrayOutputStream" && mName == "toString" &&
                                mDesc == "(Ljava/nio/charset/Charset;)Ljava/lang/String;" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "baosToString", "(Ljava/io/ByteArrayOutputStream;Ljava/nio/charset/Charset;)Ljava/lang/String;", false)

                            opcode == Opcodes.INVOKESTATIC && owner == "java/util/List" && mName == "copyOf" &&
                                mDesc == "(Ljava/util/Collection;)Ljava/util/List;" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "listCopyOf", mDesc, false)

                            opcode == Opcodes.INVOKESTATIC && owner == "java/util/Set" && mName == "copyOf" &&
                                mDesc == "(Ljava/util/Collection;)Ljava/util/Set;" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "setCopyOf", mDesc, false)

                            opcode == Opcodes.INVOKESTATIC && owner == "java/util/Map" && mName == "copyOf" &&
                                mDesc == "(Ljava/util/Map;)Ljava/util/Map;" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "mapCopyOf", mDesc, false)

                            opcode == Opcodes.INVOKESTATIC && owner == "java/util/Objects" && mName == "requireNonNullElse" &&
                                mDesc == "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "requireNonNullElse", mDesc, false)

                            opcode == Opcodes.INVOKESTATIC && owner == "java/util/Objects" && mName == "requireNonNullElseGet" &&
                                mDesc == "(Ljava/lang/Object;Ljava/util/function/Supplier;)Ljava/lang/Object;" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "requireNonNullElseGet", mDesc, false)

                            opcode == Opcodes.INVOKEVIRTUAL && owner == "java/lang/String" && mName == "isBlank" &&
                                mDesc == "()Z" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "isBlank", "(Ljava/lang/String;)Z", false)

                            opcode == Opcodes.INVOKEVIRTUAL && owner == "java/lang/String" && mName == "strip" &&
                                mDesc == "()Ljava/lang/String;" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "strip", "(Ljava/lang/String;)Ljava/lang/String;", false)

                            opcode == Opcodes.INVOKEVIRTUAL && owner == "java/lang/String" && mName == "stripLeading" &&
                                mDesc == "()Ljava/lang/String;" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "stripLeading", "(Ljava/lang/String;)Ljava/lang/String;", false)

                            opcode == Opcodes.INVOKEVIRTUAL && owner == "java/lang/String" && mName == "stripTrailing" &&
                                mDesc == "()Ljava/lang/String;" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "stripTrailing", "(Ljava/lang/String;)Ljava/lang/String;", false)

                            opcode == Opcodes.INVOKEVIRTUAL && owner == "java/lang/String" && mName == "repeat" &&
                                mDesc == "(I)Ljava/lang/String;" ->
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, helper, "repeat", "(Ljava/lang/String;I)Ljava/lang/String;", false)

                            else -> super.visitMethodInsn(opcode, owner, mName, mDesc, isInterface)
                        }
                    }
                }
            }
        }
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        val name = classData.className
        return name.startsWith("org.schabi.newpipe") ||
            name.startsWith("org.jsoup") ||
            name.startsWith("com.grack.nanojson") ||
            name.startsWith("org.mozilla")
    }
}

android {
    namespace = "com.tv.ytwallpaper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tv.ytwallpaper"
        minSdk = 26
        targetSdk = 34
        versionCode = 8
        versionName = "1.7"
    }

    buildFeatures {
        aidl = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

androidComponents {
    onVariants { variant ->
        variant.instrumentation.transformClassesWith(
            Android9CompatFactory::class.java,
            InstrumentationScope.ALL
        ) {}
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.4")
}
