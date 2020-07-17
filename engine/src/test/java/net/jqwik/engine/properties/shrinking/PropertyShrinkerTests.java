package net.jqwik.engine.properties.shrinking;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import org.mockito.*;

import net.jqwik.api.*;
import net.jqwik.api.constraints.*;
import net.jqwik.api.lifecycle.*;
import net.jqwik.engine.*;
import net.jqwik.engine.properties.*;
import net.jqwik.engine.properties.shrinking.ShrinkableTypesForTest.*;

import static java.util.Arrays.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import static net.jqwik.api.ShrinkingTestHelper.*;

class PropertyShrinkerTests {

	private final Reporter reporter = Mockito.mock(Reporter.class);
	@SuppressWarnings("unchecked")
	private final Consumer<List<Object>> falsifiedSampleReporter = Mockito.mock(Consumer.class);

	@Example
	void ifThereIsNothingToShrinkReturnOriginalValue() {
		List<Shrinkable<Object>> unshrinkableParameters = asList(Shrinkable.unshrinkable(1), Shrinkable.unshrinkable("hello"));
		Throwable originalError = failAndCatch("original error");
		FalsifiedSample originalSample = toFalsifiedSample(unshrinkableParameters, originalError);
		PropertyShrinker shrinker = new PropertyShrinker(
			originalSample,
			ShrinkingMode.FULL,
			reporter,
			falsifiedSampleReporter
		);
		PropertyShrinkingResult result = shrinker.shrink(ignore -> TryExecutionResult.falsified(null));

		assertThat(result.sample()).isEqualTo(originalSample);
		assertThat(result.steps()).isEqualTo(0);

		verifyNoInteractions(falsifiedSampleReporter);
	}

	@Example
	void ifShrinkingIsOffReturnOriginalValue() {
		List<Shrinkable<Object>> parameters = toListOfShrinkables(5, 10);
		Throwable originalError = failAndCatch("original error");
		FalsifiedSample originalSample = toFalsifiedSample(parameters, originalError);
		PropertyShrinker shrinker = new PropertyShrinker(
			originalSample,
			ShrinkingMode.OFF,
			reporter,
			falsifiedSampleReporter
		);
		PropertyShrinkingResult result = shrinker.shrink(ignore -> TryExecutionResult.falsified(null));

		assertThat(result.sample()).isEqualTo(originalSample);
		assertThat(result.steps()).isEqualTo(0);

		verifyNoInteractions(falsifiedSampleReporter);
	}

	@Example
	void shrinkAllParameters() {
		List<Shrinkable<Object>> shrinkables = toListOfShrinkables(5, 10);

		PropertyShrinker shrinker = new PropertyShrinker(
			toFalsifiedSample(shrinkables, null),
			ShrinkingMode.FULL,
			reporter,
			falsifiedSampleReporter
		);

		TestingFalsifier<List<Object>> falsifier = paramFalsifier((Integer integer1, Integer integer2) -> {
			if (integer1 == 0) return true;
			return integer2 <= 1;
		});
		PropertyShrinkingResult result = shrinker.shrink(falsifier);

		assertThat(result.sample().parameters()).isEqualTo(asList(1, 2));
		// assertThat(result.sample().shrinkables()).hasSize(2);
		assertThat(result.sample().falsifyingError()).isNotPresent();

		assertThat(result.steps()).isGreaterThan(0);
	}

	@Property(tries = 100, edgeCases = EdgeCasesMode.NONE)
	@ExpectFailure(checkResult = ShrinkToEmptyList0.class)
	boolean shrinkDependentParameters(
		@ForAll @Size(min = 0, max = 10) List<Integer> list,
		@ForAll @IntRange(min = 0, max = 100) int size
	) {
		return list.size() < size;
	}

	private class ShrinkToEmptyList0 extends ShrinkToChecker {
		@Override
		public Iterable<?> shrunkValues() {
			return Arrays.asList(Collections.emptyList(), 0);
		}
	}

	@Example
	void reportFalsifiedParameters() {
		List<Shrinkable<Object>> shrinkables = toListOfShrinkables(5, 10);
		PropertyShrinker shrinker = new PropertyShrinker(
			toFalsifiedSample(shrinkables, null),
			ShrinkingMode.FULL,
			reporter,
			falsifiedSampleReporter
		);
		shrinker.shrink(ignore -> TryExecutionResult.falsified(null));

		verify(falsifiedSampleReporter, times(15)).accept(any(List.class));
	}

	@Example
	void falsifyingErrorComesFromActualShrunkValue() {
		List<Shrinkable<Object>> shrinkables = toListOfShrinkables(5, 10);

		PropertyShrinker shrinker = new PropertyShrinker(
			toFalsifiedSample(shrinkables, failAndCatch("original")),
			ShrinkingMode.FULL,
			reporter,
			falsifiedSampleReporter
		);

		TestingFalsifier<List<Object>> falsifier = paramFalsifier((Integer integer1, Integer integer2) -> {
			if (integer1 == 0) return true;
			if (integer2 <= 1) return true;
			throw failAndCatch("shrinking");
		});
		PropertyShrinkingResult result = shrinker.shrink(falsifier);

		assertThat(result.sample().parameters()).isEqualTo(asList(1, 2));
		assertThat(result.sample().falsifyingError()).isPresent();
		assertThat(result.sample().falsifyingError().get()).hasMessage("shrinking");
	}

	@Example
	void sampleParametersAreTheRealOnes() {
		List<Shrinkable<Object>> shrinkables = asList(
			new ShrinkableList<>(asList(new OneStepShrinkable(42)), 1).asGeneric()
		);

		PropertyShrinker shrinker = new PropertyShrinker(
			toFalsifiedSample(shrinkables, null),
			ShrinkingMode.FULL,
			reporter,
			falsifiedSampleReporter
		);

		TestingFalsifier<List<Object>> falsifier = paramFalsifier((List<Integer> list) -> {
			list.add(101);
			return list.get(0) <= 1;
		});
		PropertyShrinkingResult result = shrinker.shrink(falsifier);

		List<Object> actualParameters = result.sample().parameters();
		// List<Object> freshParameters = result.sample().shrinkables().stream().map(Shrinkable::value).collect(Collectors.toList());

		assertThat(actualParameters).containsExactly(asList(2, 101));
		//TODO:
		// assertThat(freshParameters).containsExactly(asList(2));
	}

	@Example
	void differentErrorTypeDoesNotCountAsSameError() {
		List<Shrinkable<Object>> shrinkables = toListOfShrinkables(50);

		AssertionError originalError = failAndCatch("original error");
		PropertyShrinker shrinker = new PropertyShrinker(
			toFalsifiedSample(shrinkables, originalError),
			ShrinkingMode.FULL,
			reporter,
			falsifiedSampleReporter
		);

		TestingFalsifier<List<Object>> falsifier = paramFalsifier((Integer integer) -> {
			if (integer <= 10) return true;
			if (integer % 2 == 0) {
				throw failAndCatch("shrinking");
			} else {
				throw new IllegalArgumentException();
			}
		});

		PropertyShrinkingResult result = shrinker.shrink(falsifier);

		assertThat(result.sample().parameters()).isEqualTo(asList(12));
		assertThat(result.sample().falsifyingError().get()).hasMessage("shrinking");
	}

	@Example
	void differentErrorStackTraceDoesNotCountAsSameError() {
		List<Shrinkable<Object>> shrinkables = toListOfShrinkables(50);
		AssertionError originalError = failAndCatch("original error");
		PropertyShrinker shrinker = new PropertyShrinker(
			toFalsifiedSample(shrinkables, originalError),
			ShrinkingMode.FULL,
			reporter,
			falsifiedSampleReporter
		);

		TestingFalsifier<List<Object>> falsifier = paramFalsifier((Integer integer) -> {
			if (integer <= 10) return true;
			if (integer % 2 == 0) {
				throw failAndCatch("shrinking");
			} else {
				throw new RuntimeException("different location");
			}
		});

		PropertyShrinkingResult result = shrinker.shrink(falsifier);

		assertThat(result.sample().parameters()).isEqualTo(asList(12));
		assertThat(result.sample().falsifyingError().get()).hasMessage("shrinking");
	}

	@Example
	void resultSampleConsistsOfActualUsedObjects_notOfValuesGeneratedByShrinkable() {
		List<Shrinkable<Object>> shrinkables = toListOfShrinkables(5, 10);

		PropertyShrinker shrinker = new PropertyShrinker(
			toFalsifiedSample(shrinkables, null),
			ShrinkingMode.FULL,
			reporter,
			falsifiedSampleReporter
		);

		TestingFalsifier<List<Object>> falsifier = params -> {
			params.add(42);
			if (((int) params.get(0)) == 0) return true;
			if (((int) params.get(1)) <= 1) return true;
			return false;
		};
		PropertyShrinkingResult result = shrinker.shrink(falsifier);

		assertThat(result.sample().parameters()).isEqualTo(asList(1, 2, 42));
	}

	@Example
	void withBoundedShrinkingBreakOffAfter1000Steps() {
		List<Shrinkable<Object>> shrinkables = toListOfShrinkables(900, 1000);

		PropertyShrinker shrinker = new PropertyShrinker(
			toFalsifiedSample(shrinkables, null),
			ShrinkingMode.BOUNDED,
			reporter,
			falsifiedSampleReporter
		);

		PropertyShrinkingResult result = shrinker.shrink(ignore -> TryExecutionResult.falsified(null));

		assertThat(result.sample().parameters()).isEqualTo(asList(0, 900));

		verify(reporter, times(1)).publishValue(eq("shrinking bound reached"), anyString());
	}

	private List<Shrinkable<Object>> toListOfShrinkables(int... args) {
		return Arrays.stream(args).mapToObj(i -> new OneStepShrinkable(i).asGeneric()).collect(Collectors.toList());
	}

	@Group
	class Duplicates {

		@Property(tries = 10000)
		@ExpectFailure(checkResult = ShrinkTo77.class)
		boolean shrinkDuplicateIntegersTogether(
			@ForAll @IntRange(min = 1, max = 100) int int1,
			@ForAll @IntRange(min = 1, max = 100) int int2
		) {
			return int1 < 7 || int1 != int2;
		}

		private class ShrinkTo77 extends ShrinkToChecker {
			@Override
			public Iterable<?> shrunkValues() {
				return Arrays.asList(7, 7);
			}
		}

		@Property(tries = 10000)
		@ExpectFailure(checkResult = ShrunkToAA.class)
		void shrinkingDuplicateStringsTogether(@ForAll("aString") String first, @ForAll("aString") String second) {
			assertThat(first).isNotEqualTo(second);
		}

		private class ShrunkToAA extends ShrinkToChecker {
			@Override
			public Iterable<?> shrunkValues() {
				return Arrays.asList("aa", "aa");
			}
		}

		@Provide
		Arbitrary<String> aString() {
			return Arbitraries.strings().withCharRange('a', 'z').ofMinLength(2).ofMaxLength(5);
		}
	}

}
