package tech.grasshopper.data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import tech.grasshopper.pdf.data.ReportData;
import tech.grasshopper.pdf.pojo.cucumber.Feature;
import tech.grasshopper.pdf.pojo.cucumber.Hook;
import tech.grasshopper.pdf.pojo.cucumber.Hook.HookType;
import tech.grasshopper.pdf.pojo.cucumber.Scenario;
import tech.grasshopper.pdf.pojo.cucumber.Status;
import tech.grasshopper.pdf.pojo.cucumber.Step;
import tech.grasshopper.pdf.util.DateUtil;
import tech.grasshopper.pojo.Embedded;
import tech.grasshopper.pojo.Row;

public class PDFCucumberReportDataGenerator {

	public ReportData generateReportData(List<tech.grasshopper.pojo.Feature> cukeFeatures) {

		List<Feature> features = new ArrayList<>();

		for (tech.grasshopper.pojo.Feature cukeFeature : cukeFeatures) {

			List<Status> scenarioStatuses = new ArrayList<>();
			List<Scenario> scenarios = new ArrayList<>();
			LocalDateTime featureStartTime = DateUtil
					.convertToLocalDateTimeFromTimeStamp(cukeFeature.getElements().get(0).getStartTimestamp());
			LocalDateTime startTime = null;
			LocalDateTime endTime = null;

			for (tech.grasshopper.pojo.Scenario cukeScenario : cukeFeature.getElements()) {

				List<Step> steps = new ArrayList<>();
				List<Status> stepHookStatuses = new ArrayList<>();

				List<Hook> beforeHooks = new ArrayList<>();
				List<Hook> afterHooks = new ArrayList<>();
				startTime = DateUtil.convertToLocalDateTimeFromTimeStamp(cukeScenario.getStartTimestamp());

				startTime = createHooks(cukeScenario.getBefore(), startTime, beforeHooks, HookType.BEFORE,
						stepHookStatuses);

				for (tech.grasshopper.pojo.Step cukeStep : cukeScenario.getSteps()) {
					List<Hook> beforeStepHooks = new ArrayList<>();
					List<Hook> afterStepHooks = new ArrayList<>();

					startTime = createHooks(cukeStep.getBefore(), startTime, beforeStepHooks, HookType.BEFORE_STEP,
							stepHookStatuses);

					endTime = startTime.plusNanos(cukeStep.getResult().getDuration());

					Step step = createStep(cukeStep, beforeStepHooks, afterStepHooks, startTime, endTime);
					steps.add(step);

					startTime = LocalDateTime.from(endTime);
					stepHookStatuses.add(convertStatus(cukeStep.getResult().getStatus()));

					startTime = createHooks(cukeStep.getAfter(), startTime, afterStepHooks, HookType.AFTER_STEP,
							stepHookStatuses);
				}

				startTime = createHooks(cukeScenario.getAfter(), startTime, afterHooks, HookType.AFTER,
						stepHookStatuses);

				Scenario scenario = createScenario(cukeScenario, steps, stepHookStatuses, beforeHooks, afterHooks,
						startTime);
				scenarios.add(scenario);
				scenarioStatuses.add(Collections.max(stepHookStatuses));
			}

			Feature feature = createFeature(cukeFeature, scenarios, scenarioStatuses, featureStartTime, startTime);
			features.add(feature);
		}
		return ReportData.builder().features(features).build();
	}

	private Feature createFeature(tech.grasshopper.pojo.Feature cukeFeature, List<Scenario> scenarios,
			List<Status> scenarioStatuses, LocalDateTime featureStartTime, LocalDateTime endTime) {
		Feature feature = Feature.builder().name(cukeFeature.getName())
				.tags(cukeFeature.getTags().stream().map(t -> t.getName()).collect(Collectors.toList()))
				.scenarios(scenarios).status(Collections.max(scenarioStatuses)).startTime(featureStartTime)
				.endTime(endTime).build();
		return feature;
	}

	private Scenario createScenario(tech.grasshopper.pojo.Scenario cukeScenario, List<Step> steps,
			List<Status> stepHookStatuses, List<Hook> beforeHooks, List<Hook> afterHooks, LocalDateTime endTime) {
		Scenario scenario = Scenario.builder().name(cukeScenario.getName())
				.tags(cukeScenario.getTags().stream().map(t -> t.getName()).collect(Collectors.toList())).steps(steps)
				.before(beforeHooks).after(afterHooks).status(Collections.max(stepHookStatuses))
				.startTime(DateUtil.convertToLocalDateTimeFromTimeStamp(cukeScenario.getStartTimestamp()))
				.endTime(endTime).build();
		return scenario;
	}

	private Step createStep(tech.grasshopper.pojo.Step cukeStep, List<Hook> beforeStepHooks, List<Hook> afterStepHooks,
			LocalDateTime startTime, LocalDateTime endTime) {
		Step step = Step.builder().name(cukeStep.getKeyword() + " " + cukeStep.getName()).before(beforeStepHooks)
				.after(afterStepHooks).status(convertStatus(cukeStep.getResult().getStatus()))
				.keyword(cukeStep.getKeyword()).docString(cukeStep.getDocString().getValue())
				.rows(convertRows(cukeStep.getRows())).errorMessage(cukeStep.getResult().getErrorMessage())
				.output(cukeStep.getOutput()).media(getMediaData(cukeStep.getEmbeddings())).startTime(startTime)
				.endTime(endTime).build();
		return step;
	}

	private LocalDateTime createHooks(List<tech.grasshopper.pojo.Hook> hooks, LocalDateTime startTime,
			List<Hook> collectHooks, HookType hookType, List<Status> statuses) {
		LocalDateTime endTime = LocalDateTime.from(startTime);
		for (tech.grasshopper.pojo.Hook hook : hooks) {
			endTime = startTime.plusNanos(hook.getResult().getDuration());
			collectHooks.add(
					Hook.builder().hookType(hookType).status(convertStatus(hook.getResult().getStatus().toUpperCase()))
							.location(hook.getMatch().getLocation()).errorMessage(hook.getResult().getErrorMessage())
							.output(hook.getOutput()).media(getMediaData(hook.getEmbeddings())).startTime(startTime)
							.endTime(endTime).build());
			startTime = LocalDateTime.from(endTime);
			statuses.add(convertStatus(hook.getResult().getStatus()));
		}
		return endTime;
	}

	private Status convertStatus(String statusStr) {
		Status status = Status.SKIPPED;
		if (statusStr.equalsIgnoreCase("passed"))
			status = Status.PASSED;
		else if (statusStr.equalsIgnoreCase("failed"))
			status = Status.FAILED;
		return status;
	}

	private List<String> getMediaData(List<Embedded> embeddings) {
		return embeddings.stream().map(e -> e.getFilePath()).collect(Collectors.toList());
	}

	private List<tech.grasshopper.pdf.pojo.cucumber.Row> convertRows(List<Row> rows) {
		List<tech.grasshopper.pdf.pojo.cucumber.Row> pdfRows = new ArrayList<>();
		rows.forEach(r -> {
			pdfRows.add(tech.grasshopper.pdf.pojo.cucumber.Row.builder().cells(r.getCells()).build());
		});
		return pdfRows;
	}
}
