plugins {
    id ("java")
    id("application")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(files("C:\\Users\\dfell\\IdeaProjects\\VerteilteSysteme\\lib\\messaging.jar"))
    implementation(files("C:\\Users\\dfell\\IdeaProjects\\VerteilteSysteme\\lib\\messaging-javadoc.jar"))
    testImplementation ("org.junit.jupiter:junit-jupiter-api:5.8.2")
    testRuntimeOnly ("org.junit.jupiter:junit-jupiter-engine:5.8.2")
}

tasks.test {
    useJUnitPlatform()
}