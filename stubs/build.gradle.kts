// Compile-only signatures of framework classes this module calls directly but
// the public SDK leaves out. Never packaged: the device's own classes are used.
plugins {
    `java-library`
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
