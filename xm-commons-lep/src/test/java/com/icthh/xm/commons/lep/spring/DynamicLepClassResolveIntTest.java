package com.icthh.xm.commons.lep.spring;

import com.icthh.xm.commons.config.client.service.TenantAliasService;
import com.icthh.xm.commons.lep.XmLepScriptConfigServerResourceLoader;
import com.icthh.xm.commons.security.XmAuthenticationContext;
import com.icthh.xm.commons.security.spring.config.XmAuthenticationContextConfiguration;
import com.icthh.xm.commons.tenant.TenantContext;
import com.icthh.xm.commons.tenant.TenantKey;
import com.icthh.xm.commons.tenant.spring.config.TenantContextConfiguration;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.InputStream;
import java.util.Optional;

import static com.icthh.xm.commons.config.client.service.TenantAliasService.TENANT_ALIAS_CONFIG;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_AUTH_CONTEXT;
import static com.icthh.xm.commons.lep.XmLepConstants.THREAD_CONTEXT_KEY_TENANT_CONTEXT;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 *
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {
        DynamicLepTestConfig.class,
        TenantContextConfiguration.class,
        XmAuthenticationContextConfiguration.class
})
@ActiveProfiles("resolveclasstest")
@TestPropertySource(properties = "application.lep.full-recompile-on-lep-update=true")
public class DynamicLepClassResolveIntTest {

    @Autowired
    private SpringLepManager lepManager;

    @Mock
    private TenantContext tenantContext;

    @Mock
    private XmAuthenticationContext authContext;

    @Autowired
    private DynamicTestLepService testLepService;

    @Autowired
    private TenantAliasService tenantAliasService;

    @Autowired
    private XmLepScriptConfigServerResourceLoader resourceLoader;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);

        lepManager.beginThreadContext(ctx -> {
            ctx.setValue(THREAD_CONTEXT_KEY_TENANT_CONTEXT, tenantContext);
            ctx.setValue(THREAD_CONTEXT_KEY_AUTH_CONTEXT, authContext);
        });
    }

    @Test
    @SneakyThrows
    public void testResolvingClassFromCommons() {
        runTest("msCommons", "TEST.testApp.lep.commons.folder", "TEST/testApp/lep/commons/folder");
    }

    @Test
    @SneakyThrows
    public void testResolvingClassFromParentTenant() {
        tenantAliasService.onRefresh(TENANT_ALIAS_CONFIG, loadFile("lep/TenantAlias.yml"));
        runTest("msCommons", "PARENT.testApp.lep.commons.folder", "TEST/testApp/lep/commons/folder");
    }


    @Test
    @SneakyThrows
    public void testResolvingClassFromTenantLevelCommons() {
        runTest("tenantCommons", "TEST.commons.lep.folder", "TEST/commons/lep/folder");
    }

    @Test
    @SneakyThrows
    public void testResolvingClassFromEnvLevelCommons() {
        runTest("envCommons", "commons.lep.folder", "commons/lep/folder");
    }

    @Test
    @SneakyThrows
    public void testEnumInterfaceAnnotationResolving() {
        when(tenantContext.getTenantKey()).thenReturn(Optional.of(TenantKey.valueOf("TEST")));
        // this sleep is needed because groovy has debounce time to lep update
        Thread.sleep(100);
        resourceLoader.onRefresh("/config/tenants/TEST/testApp/lep/service/TestLepMethod$$around.groovy",
                loadFile("lep/TestEnumInterfaceAnnotationUsage")
        );
        loadDeclarationLep("TestEnumDeclaration");
        loadDeclarationLep("TestInterfaceDeclaration");
        loadDeclarationLep("TestAnnotationDeclaration");
        loadDeclarationLep("TestEnumInterfaceAnnotationDeclaration");

        String result = testLepService.testLepMethod();
        assertEquals("VAL1", result);
    }

    private void loadDeclarationLep(String testEnumDeclaration) {
        String testClassDeclarationPath = "/config/tenants/TEST/testApp/lep/commons/folder/" + testEnumDeclaration + "$$tenant.groovy";
        String testClassBody = loadFile("lep/" + testEnumDeclaration);
        resourceLoader.onRefresh(testClassDeclarationPath, testClassBody);
    }


    private void runTest(String suffix, String packageName, String path) throws InterruptedException {
        when(tenantContext.getTenantKey()).thenReturn(Optional.of(TenantKey.valueOf("TEST")));
        // this sleep is needed because groovy has debounce time to lep update
        Thread.sleep(100);
        resourceLoader.onRefresh("/config/tenants/TEST/testApp/lep/service/TestLepMethod$$around.groovy",
                loadFile("lep/TestClassUsage")
                        .replace("${package}", packageName)
                        .replace("${suffix}", suffix)
        );
        String testClassDeclarationPath = "/config/tenants/" + path + "/TestClassDeclaration" + suffix + "$$tenant.groovy";
        String testClassBody = loadFile("lep/TestClassDeclaration")
                .replace("${package}", packageName)
                .replace("${suffix}", suffix)
                .replace("${value}", "I am class in lep!");
        resourceLoader.onRefresh(testClassDeclarationPath, testClassBody);

        String result = testLepService.testLepMethod();
        assertEquals("I am class in lep!", result);

        // this sleep is needed because groovy has debounce time to lep update
        Thread.sleep(100);
        resourceLoader.onRefresh(testClassDeclarationPath,
                loadFile("lep/TestClassDeclaration")
                        .replace("${package}", packageName)
                        .replace("${suffix}", suffix)
                        .replace("${value}", "I am updated class in lep!"));
        result = testLepService.testLepMethod();
        assertEquals("I am updated class in lep!", result);
    }

    @Test
    @SneakyThrows
    public void testReloadLepClass() {
        when(tenantContext.getTenantKey()).thenReturn(Optional.of(TenantKey.valueOf("TEST")));
        // this sleep is needed because groovy has debounce time to lep update
        Thread.sleep(100);
        resourceLoader.onRefresh("/config/tenants/TEST/testApp/lep/service/TestLepMethod$$around.groovy",
                loadFile("lep/TestLoadClassByName")
                        .replace("${package}", "commons.lep.folder")
                        .replace("${suffix}", "ReloadClass")
        );

        String testClassDeclarationPath = "/config/tenants/commons/lep/folder/TestClassDeclarationReloadClass$$tenant.groovy";
        String testClassBody = loadFile("lep/TestClassDeclaration")
                .replace("${package}", "commons.lep.folder")
                .replace("${suffix}", "ReloadClass")
                .replace("${value}", "I am class in lep!");
        resourceLoader.onRefresh(testClassDeclarationPath, testClassBody);

        String result = testLepService.testLepMethod();
        assertEquals("I am class in lep!", result);

        resourceLoader.onRefresh(testClassDeclarationPath,
                loadFile("lep/TestClassDeclaration")
                        .replace("${package}", "commons.lep.folder")
                        .replace("${suffix}", "ReloadClass")
                        .replace("${value}", "I am updated class in lep!"));
        // this sleep is needed because groovy has debounce time to lep update
        Thread.sleep(110);
        result = testLepService.testLepMethod();
        assertEquals("I am updated class in lep!", result);
    }

    @SneakyThrows
    public static String loadFile(String path) {
        try (InputStream cfgInputStream = new ClassPathResource(path).getInputStream()) {
            return IOUtils.toString(cfgInputStream, UTF_8);
        }
    }

}
