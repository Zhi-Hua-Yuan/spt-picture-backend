package com.spt.sptpicturebackend.architecture;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.spt.sptpicturebackend")
class SearchBoundaryTest {
    @ArchTest
    static final ArchRule searchDoesNotReachCorePersistence = noClasses()
            .that().resideInAnyPackage("..search..")
            .should().dependOnClassesThat()
            .resideInAnyPackage("com.spt.sptpicturebackend.controller..",
                    "com.spt.sptpicturebackend.mapper..",
                    "com.spt.sptpicturebackend.service.impl..");
}
