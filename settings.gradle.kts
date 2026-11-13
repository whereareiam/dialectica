pluginManagement { includeBuild("build-logic") }

rootProject.name = "Dialectica"

include("dialectica-api")
include("dialectica-common")
include("dialectica-dialect:dialect-sqlite")
include("dialectica-dialect:dialect-h2")
include("dialectica-dialect:dialect-postgresql")
include("dialectica-dialect:dialect-mysql")
include("dialectica-dialect:dialect-mariadb")
include("dialectica-adapter:adapter-jdbi")
include("dialectica")
include("dialectica-processor")
include("dialectica-bundle")
