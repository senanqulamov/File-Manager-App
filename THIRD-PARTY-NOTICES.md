# Third-party components

Docket uses third-party software whose notices and licences remain separate from Docket's commercial terms. This file is a source inventory and release preparation guide, not a complete notice bundle for a binary distribution.

| Component | Where declared / used |
| --- | --- |
| Spring Boot and Spring libraries | [Server POM](server/pom.xml) |
| PostgreSQL JDBC and H2 | [Server POM](server/pom.xml) |
| Apache PDFBox, Apache POI, Commons Compress, XZ | [Server POM](server/pom.xml) |
| Bouncy Castle, zip4j, TwelveMonkeys WebP | [Server POM](server/pom.xml) |
| JavaFX and Jackson | [Desktop POM](desktop/pom.xml) |
| Urbanist | [Bundled SIL Open Font License](desktop/src/main/resources/com/pmis/docket/desktop/fonts/OFL.txt) |
| Java runtime | Included by the Windows packaging process; depends on the build JDK distribution |
| LibreOffice and FFmpeg | Optional separately installed server tools |
| Inno Setup | Windows installer build tool |

For each release, generate the resolved dependency inventory with Maven (`mvn dependency:tree`), collect the actual dependency and runtime notices, and review redistribution requirements for the exact artifacts being shipped. Keep any upstream notices included in JARs or the bundled runtime. Optional tools and codecs require their own distribution review if bundled in a future package.

The product screenshots and design direction come from the supplied Docket handover. They are identified as design previews in the showcase.
