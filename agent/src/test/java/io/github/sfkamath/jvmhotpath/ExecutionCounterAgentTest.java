package io.github.sfkamath.jvmhotpath;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.instrument.ClassFileTransformer;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExecutionCounterAgentTest {

    @Test
    void testArgumentParsing() {
        ExecutionCounterAgent agent = new ExecutionCounterAgent();
        String args = "packages=com.example,com.test,exclude=com.example.Exclude,flushInterval=10,output=target/test.html,sourcepath=src/main/java,verbose=true";
        
        agent.parseArguments(args);
        
        List<String> includes = agent.getIncludePackages();
        assertEquals(2, includes.size());
        assertTrue(includes.contains("com/example"), "Found: " + includes);
        assertTrue(includes.contains("com/test"), "Found: " + includes);
        
        List<String> excludes = agent.getExcludePackages();
        assertEquals(1, excludes.size());
        assertTrue(excludes.contains("com/example/Exclude"));
        
        assertEquals(10, agent.getFlushInterval());
        assertEquals("target/test.html", agent.getOutputFile());
        assertEquals("src/main/java", agent.getSourcePath());
        assertTrue(agent.isVerbose());
    }

    @Test
    void testTransformationEligibility() throws Exception {
        ExecutionCounterAgent agent = new ExecutionCounterAgent();
        agent.parseArguments("packages=com.example");
        
        ClassFileTransformer transformer = agent.getTransformer();
        
        // Should NOT instrument JDK classes
        assertNull(transformer.transform(null, "java/lang/String", null, null, new byte[0]));
        
        // Should NOT instrument agent's own classes
        assertNull(transformer.transform(null, "io/github/sfkamath/jvmhotpath/ExecutionCountStore", null, null, new byte[0]));
        
        // Should NOT instrument Micronaut/Netty
        assertNull(transformer.transform(null, "io/micronaut/http/HttpRequest", null, null, new byte[0]));

        // SHOULD NOT instrument if not in packages
        assertNull(transformer.transform(null, "org/other/App", null, null, new byte[0]));

        // SHOULD instrument matched package (with valid-looking class name)
        // Note: transform will still fail with ClassReader if byte[] is empty, returning null in the catch block.
        // So we can't easily distinguish between "decided not to instrument" and "failed to instrument" 
        // just by checking null if we pass empty bytes.
    }
}