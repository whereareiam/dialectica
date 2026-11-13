plugins {
    `java-test-fixtures`
    id("library")
}

// The bundle has no code of its own: it is Dialectica with every dialect, as one dependency. Its
// tests run the schema writer against all five databases. Its test fixtures get their typed columns
// from the processor, which the adapters' tests use.
dependencies {
    api(project(":dialectica"))
    api(project(":dialectica-dialect:dialect-h2"))
    api(project(":dialectica-dialect:dialect-mariadb"))
    api(project(":dialectica-dialect:dialect-mysql"))
    api(project(":dialectica-dialect:dialect-postgresql"))
    api(project(":dialectica-dialect:dialect-sqlite"))

    testFixturesApi(project(":dialectica-api"))
    testFixturesCompileOnly(rootProject.libs.lombok)
    testFixturesAnnotationProcessor(project(":dialectica-processor"))
    testFixturesAnnotationProcessor(rootProject.libs.lombok)

    testImplementation(rootProject.libs.h2)
    testImplementation(rootProject.libs.mariadb)
    testImplementation(rootProject.libs.mysql)
    testImplementation(rootProject.libs.postgresql)
    testImplementation(rootProject.libs.sqlite)
    testImplementation(rootProject.libs.bundles.testing)

    testRuntimeOnly(rootProject.libs.junit.platform)
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "dialectica-bundle"
            pom {
                name.set("dialectica-bundle")
                description.set("Dialectica with every dialect, as one dependency")
            }
        }
    }
}

// Test fixtures are shared with the adapters' tests and are not published.
val javaComponent = components["java"] as AdhocComponentWithVariants
javaComponent.withVariantsFromConfiguration(configurations["testFixturesApiElements"]) { skip() }
javaComponent.withVariantsFromConfiguration(configurations["testFixturesRuntimeElements"]) { skip() }
