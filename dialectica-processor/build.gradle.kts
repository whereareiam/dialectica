plugins { id("library") }

// The processor runs inside javac and reads entities as source elements, so it depends on no
// Dialectica module; its tests compile entities against the entry point.
dependencies {
    testImplementation(project(":dialectica"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "dialectica-processor"
            pom {
                name.set("dialectica-processor")
                description.set("Annotation processor that writes the typed columns of Dialectica entities")
            }
        }
    }
}
