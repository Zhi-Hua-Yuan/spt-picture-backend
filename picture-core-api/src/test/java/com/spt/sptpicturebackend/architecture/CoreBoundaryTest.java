package com.spt.sptpicturebackend.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.spt.sptpicturebackend")
class CoreBoundaryTest {
    @ArchTest
    static final ArchRule coreDoesNotReachWorkerPersistence = noClasses()
            .that().resideInAnyPackage("..controller..", "..service..", "..search..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..media.persistence..", "..search.persistence..");
}
