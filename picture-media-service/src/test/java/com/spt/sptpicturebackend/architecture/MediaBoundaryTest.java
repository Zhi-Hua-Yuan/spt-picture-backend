package com.spt.sptpicturebackend.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.spt.sptpicturebackend")
class MediaBoundaryTest {
    @ArchTest
    static final ArchRule mediaDoesNotReachCoreAdapters = noClasses()
            .that().resideInAnyPackage("..media..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("..controller..", "..service.impl..", "..search.persistence..");
}
