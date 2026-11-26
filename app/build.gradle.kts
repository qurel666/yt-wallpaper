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

abstract class UrlDecoderFixFactory : AsmClassVisitorFactory<InstrumentationParameters.None> {
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
                        if (opcode == Opcodes.INVOKESTATIC &&
                            owner == "java/net/URLDecoder" &&
                            mName == "decode" &&
                            mDesc == "(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;"
                        ) {
                            super.visitMethodInsn(
                                Opcodes.INVOKESTATIC,
                                "com/tv/ytwallpaper/CompatHelper",
                                "urlDecode",
                                "(Ljava/lang/String;Ljava/nio/charset/Charset;)Ljava/lang/String;",
                                false
                            )
                        } else {
                            super.visitMethodInsn(opcode, owner, mName, mDesc, isInterface)
                        }
                    }
                }
            }
        }
    }

    override fun isInstrumentable(classData: ClassData): Boolean {
        return classData.className.startsWith("org.schabi.newpipe")
    }
}

android {
    namespace = "com.tv.ytwallpaper"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.tv.ytwallpaper"
        minSdk = 26
        targetSdk = 34
        versionCode = 6
        versionName = "1.5"
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
            UrlDecoderFixFactory::class.java,
            InstrumentationScope.ALL
        ) {}
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.0.4")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.github.TeamNewPipe:NewPipeExtractor:v0.24.4")
}
