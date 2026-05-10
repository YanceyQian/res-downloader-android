// Gradle初始化脚本 - 禁用源代码下载
initscript {
    repositories {
        google()
        mavenCentral()
    }
}

allprojects {
    repositories {
        google()
        mavenCentral()
    }
    
    // 禁用源代码和文档下载
    configurations.all {
        exclude(group = "gradle", module = "gradle")
    }
}

// 禁用Kotlin DSL源代码解析
rootProject {
    plugins.withId("org.gradle.kotlin.kotlin-dsl") {
        // 禁用源代码解析
    }
}
