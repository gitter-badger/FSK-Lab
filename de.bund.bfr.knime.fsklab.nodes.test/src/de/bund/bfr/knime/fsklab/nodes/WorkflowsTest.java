package de.bund.bfr.knime.fsklab.nodes;

import java.io.File;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.knime.testing.core.TestrunConfiguration;

import nl.esciencecenter.e3dchem.knime.testing.TestFlowRunner;

public class WorkflowsTest {

	@Rule
	public ErrorCollector collector = new ErrorCollector();
	
	private TestFlowRunner runner;
	
	@Before
	public void setUp() {
		TestrunConfiguration runConfiguration = new TestrunConfiguration();
		runConfiguration.setTestDialogs(false);
		runConfiguration.setReportDeprecatedNodes(true);
		runConfiguration.setCheckMemoryLeaks(false);
		runConfiguration.setLoadSaveLoad(false);
		runner = new TestFlowRunner(collector, runConfiguration);
	}
	
	@Test
	public void testChickenModels() throws Exception {
		runner.runTestWorkflow(new File("workflows/InitializeParentsAnimals"));
		runner.runTestWorkflow(new File("workflows/InitializeParentsFlocks"));
		runner.runTestWorkflow(new File("workflows/HorizontalTransmiAnimals"));
		runner.runTestWorkflow(new File("workflows/HorizontalTransmiFlocks"));
	}
}
