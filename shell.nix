{ pkgs ? import <nixpkgs> {} }:

pkgs.mkShell {
  buildInputs = [
    pkgs.openjdk17    # Java 17
    pkgs.gradle       # Gradle CLI
    pkgs.sqlite       # sqlite3 CLI
  ];

  # point JAVA_HOME so Gradle/Spring Boot picks it up
  shellHook = ''
    export JAVA_HOME=${pkgs.openjdk17}
  '';
}
