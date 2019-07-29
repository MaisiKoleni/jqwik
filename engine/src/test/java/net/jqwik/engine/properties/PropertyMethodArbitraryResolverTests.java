package net.jqwik.engine.properties;

import java.lang.annotation.*;
import java.util.*;

import net.jqwik.api.*;
import net.jqwik.api.arbitraries.*;
import net.jqwik.api.constraints.*;
import net.jqwik.api.domains.*;
import net.jqwik.api.providers.*;
import net.jqwik.engine.*;
import net.jqwik.engine.properties.arbitraries.*;
import net.jqwik.engine.support.*;

import static org.assertj.core.api.Assertions.*;

@Group
class PropertyMethodArbitraryResolverTests {

	private static class Thing {

	}

	@Target({ ElementType.METHOD })
	@Retention(RetentionPolicy.RUNTIME)
	@Provide
	@interface MyProvide {

	}


	@Group
	class RegisteredArbitraryResolvers {

		@Example
		void defaultProvidersAreUsedIfNothingIsProvided() {
			PropertyMethodArbitraryResolver provider = getResolver(DefaultParams.class);
			MethodParameter parameter = getParameter(DefaultParams.class, "intParam");
			Set<Arbitrary<?>> arbitraries = provider.forParameter(parameter);
			assertThat(arbitraries).hasSize(1);
			assertThat(arbitraries.iterator().next()).isInstanceOf(DefaultIntegerArbitrary.class);
		}

		@Example
		void doNotConsiderRegisteredProvidersIfForAllHasValue() {
			PropertyMethodArbitraryResolver resolver = getResolver(DefaultParams.class);
			MethodParameter parameter = getParameter(DefaultParams.class, "intParamWithForAllValue");
			assertThat(resolver.forParameter(parameter)).isEmpty();
		}

		@Example
		void useNextRegisteredProviderIfFirstDoesNotProvideAnArbitrary() {
			Arbitrary<String> secondArbitrary = tries -> random -> Shrinkable.unshrinkable("an arbitrary string");
			List<ArbitraryProvider> registeredProviders = Arrays.asList(
				createProvider(String.class),
				createProvider(String.class, secondArbitrary)
			);
			PropertyMethodArbitraryResolver resolver = new PropertyMethodArbitraryResolver(
				DefaultParams.class, new DefaultParams(),
				new RegisteredArbitraryResolver(registeredProviders),
				new RegisteredArbitraryConfigurer(Collections.emptyList())
			);
			MethodParameter parameter = getParameter(DefaultParams.class, "aString");
			assertThat(resolver.forParameter(parameter).iterator().next()).isSameAs(secondArbitrary);
		}

		@Example
		void resolveSeveralFittingArbitraries() {
			Arbitrary<String> doesNotFitFit = tries -> random -> Shrinkable.unshrinkable("an arbitrary string");
			Arbitrary<String> firstFit = tries -> random -> Shrinkable.unshrinkable("an arbitrary string");
			Arbitrary<String> secondFit = tries -> random -> Shrinkable.unshrinkable("an arbitrary string");
			Arbitrary<String> thirdFit = tries -> random -> Shrinkable.unshrinkable("an arbitrary string");
			List<ArbitraryProvider> registeredProviders = Arrays.asList(
				createProvider(String.class, firstFit),
				createProvider(Integer.class, doesNotFitFit),
				createProvider(String.class, secondFit),
				createProvider(String.class, thirdFit)
			);
			PropertyMethodArbitraryResolver resolver = new PropertyMethodArbitraryResolver(
				DefaultParams.class, new DefaultParams(),
				new RegisteredArbitraryResolver(registeredProviders),
				new RegisteredArbitraryConfigurer(Collections.emptyList())
			);
			MethodParameter parameter = getParameter(DefaultParams.class, "aString");
			assertThat(resolver.forParameter(parameter)).containsOnly(firstFit, secondFit, thirdFit);
		}

		@Example
		void allFittingArbitrariesAreConfigured() {
			final List<Arbitrary> configured = new ArrayList<>();
			RegisteredArbitraryConfigurer configurer = new RegisteredArbitraryConfigurer(Collections.emptyList()) {
				@Override
				public Arbitrary<?> configure(Arbitrary<?> arbitrary, TypeUsage targetType) {
					configured.add(arbitrary);
					return arbitrary;
				}
			};

			Arbitrary<String> firstFit = tries -> random -> Shrinkable.unshrinkable("an arbitrary string");
			Arbitrary<String> secondFit = tries -> random -> Shrinkable.unshrinkable("an arbitrary string");
			List<ArbitraryProvider> registeredProviders = Arrays.asList(
				createProvider(String.class, firstFit),
				createProvider(String.class, secondFit)
			);
			PropertyMethodArbitraryResolver resolver = new PropertyMethodArbitraryResolver(
				DefaultParams.class, new DefaultParams(),
				new RegisteredArbitraryResolver(registeredProviders),
				configurer
			);
			MethodParameter parameter = getParameter(DefaultParams.class, "stringOfLength5");
			assertThat(resolver.forParameter(parameter)).containsOnly(firstFit, secondFit);
			assertThat(configured).containsOnly(firstFit, secondFit);
		}

		private ArbitraryProvider createProvider(Class targetClass, Arbitrary<?>... arbitraries) {
			return new ArbitraryProvider() {
				@Override
				public boolean canProvideFor(TypeUsage targetType) {
					return targetType.isAssignableFrom(targetClass);
				}

				@Override
				public Set<Arbitrary<?>> provideFor(TypeUsage targetType, SubtypeProvider subtypeProvider) {
					return new HashSet<>(Arrays.asList(arbitraries));
				}
			};
		}

		private class DefaultParams {
			@Property
			boolean intParam(@ForAll int aValue) {
				return true;
			}

			@Property
			boolean intParamWithForAllValue(@ForAll("someInt") int aValue) {
				return true;
			}

			@Property
			boolean aString(@ForAll String aString) {
				return true;
			}

			@Property
			boolean stringOfLength5(@ForAll @StringLength(5) String aString) {
				return true;
			}
		}

	}

	@Group
	class ProvidedArbitraries {

		@Example
		void findBoxedTypeGenerator() {
			PropertyMethodArbitraryResolver provider = getResolver(WithNamedProviders.class);
			MethodParameter parameter = getParameter(WithNamedProviders.class, "longFromBoxedType");
			Set<Arbitrary<?>> arbitraries = provider.forParameter(parameter);
			assertThat(arbitraries.iterator().next()).isInstanceOf(LongArbitrary.class);
		}

		@Example
		void findGeneratorByName() {
			PropertyMethodArbitraryResolver provider = getResolver(WithNamedProviders.class);
			MethodParameter parameter = getParameter(WithNamedProviders.class, "thing");
			Set<Arbitrary<?>> arbitraries = provider.forParameter(parameter);
			assertThingArbitrary(arbitraries.iterator().next());
		}

		@Example
		void findGeneratorByNameInFromAnnotation() {
			PropertyMethodArbitraryResolver provider = getResolver(WithNamedProviders.class);
			MethodParameter parameter = getParameter(WithNamedProviders.class, "thingFrom");
			Set<Arbitrary<?>> arbitraries = provider.forParameter(parameter);
			assertThingArbitrary(arbitraries.iterator().next());
		}

		@Example
		void findGeneratorByNameInFromAnnotationOfTypeParameter(@ForAll Random random) {
			PropertyMethodArbitraryResolver provider = getResolver(WithNamedProviders.class);
			MethodParameter parameter = getParameter(WithNamedProviders.class, "listOfThingFrom");
			Set<Arbitrary<?>> arbitraries = provider.forParameter(parameter);
			Arbitrary<?> listOfThingsArbitrary = arbitraries.iterator().next();
			List listOfThings = (List) listOfThingsArbitrary.generator(10).next(random).value();
			//noinspection unchecked
			assertThat(listOfThings).allMatch(aThing -> aThing instanceof Thing);
		}

		@Example
		void findGeneratorByMethodName() {
			PropertyMethodArbitraryResolver provider = getResolver(WithNamedProviders.class);
			MethodParameter parameter = getParameter(WithNamedProviders.class, "thingByMethodName");
			Set<Arbitrary<?>> arbitraries = provider.forParameter(parameter);
			assertThat(arbitraries.iterator().next()).isInstanceOf(Arbitrary.class);
		}

		@Example
		void providedArbitraryIsConfigured() {
			final List<Arbitrary> configured = new ArrayList<>();
			RegisteredArbitraryConfigurer configurer = new RegisteredArbitraryConfigurer(Collections.emptyList()) {
				@Override
				public Arbitrary<?> configure(Arbitrary<?> arbitrary, TypeUsage targetType) {
					configured.add(arbitrary);
					return arbitrary;
				}
			};

			PropertyMethodArbitraryResolver provider = new PropertyMethodArbitraryResolver(
				WithNamedProviders.class, new WithNamedProviders(),
				new RegisteredArbitraryResolver(Collections.emptyList()),
				configurer
			);
			MethodParameter parameter = getParameter(WithNamedProviders.class, "thingWithNullByMethodName");
			Set<Arbitrary<?>> arbitraries = provider.forParameter(parameter);
			Arbitrary<?> arbitrary = arbitraries.iterator().next();
			assertThat(configured).containsOnly(arbitrary);
		}

		@Example
		void findGeneratorByMethodNameOutsideGroup() {
			PropertyMethodArbitraryResolver provider = getResolver(WithNamedProviders.NestedWithNamedProviders.class);
			MethodParameter parameter = getParameter(WithNamedProviders.NestedWithNamedProviders.class, "nestedThingByMethodName");
			Set<Arbitrary<?>> arbitraries = provider.forParameter(parameter);
			assertThingArbitrary(arbitraries.iterator().next());
		}

		@Example
		void findGeneratorByNameOutsideGroup(@ForAll Random random) {
			PropertyMethodArbitraryResolver provider = getResolver(WithNamedProviders.NestedWithNamedProviders.class);
			MethodParameter parameter = getParameter(WithNamedProviders.NestedWithNamedProviders.class, "nestedThing");
			Set<Arbitrary<?>> arbitraries = provider.forParameter(parameter);
			assertThingArbitrary(arbitraries.iterator().next());
		}

		@Example
		void findGeneratorByNameWithProvideAnnotationInSuperclass() {
			PropertyMethodArbitraryResolver provider = getResolver(WithNamedProviders.class);
			MethodParameter parameter = getParameter(WithNamedProviders.class, "thingFromProvideAnnotatedInSuperclass");
			Set<Arbitrary<?>> arbitraries = provider.forParameter(parameter);
			assertThingArbitrary(arbitraries.iterator().next());
		}

		@Example
		void findGeneratorByNameWithProvideInMetaAnnotation() {
			PropertyMethodArbitraryResolver provider = getResolver(WithNamedProviders.class);
			MethodParameter parameter = getParameter(WithNamedProviders.class, "thingFromProvideInMetaAnnotation");
			Set<Arbitrary<?>> arbitraries = provider.forParameter(parameter);
			assertThingArbitrary(arbitraries.iterator().next());
		}

		@Example
		void namedGeneratorNotFound() {
			PropertyMethodArbitraryResolver provider = getResolver(WithNamedProviders.class);
			MethodParameter parameter = getParameter(WithNamedProviders.class, "otherThing");
			assertThat(provider.forParameter(parameter)).isEmpty();
		}

		private void assertThingArbitrary(Arbitrary<?> arbitrary) {
			Thing aThing = (Thing) arbitrary.generator(10).next(SourceOfRandomness.current()).value();
			assertThat(aThing).isInstanceOf(Thing.class);
		}

		private abstract class AbstractNamedProviders {
			@Provide
			abstract Arbitrary<Thing> thingFromSuper();
		}


		private class WithNamedProviders extends AbstractNamedProviders {
			@Property
			boolean thing(@ForAll("aThingByValue") Thing aThing) {
				return true;
			}

			@Provide("aThingByValue")
			Arbitrary<Thing> aThingy() {
				return Arbitraries.constant(new Thing());
			}

			@Property
			boolean otherThing(@ForAll("unknown ref") Thing aThing) {
				return true;
			}

			@Property
			boolean thingByMethodName(@ForAll("byMethodName") Thing aString) {
				return true;
			}

			@Property
			boolean thingWithNullByMethodName(@ForAll("byMethodName") @WithNull Thing aThing) {
				return true;
			}

			@Provide
			Arbitrary<Thing> byMethodName() {
				return Arbitraries.constant(new Thing());
			}

			@Property
			boolean longFromBoxedType(@ForAll("longBetween1and10") long aLong) {
				return true;
			}

			@Provide
			Arbitrary<Long> longBetween1and10() {
				return Arbitraries.longs().between(1L, 10L);
			}

			@Property
			boolean thingFromProvideAnnotatedInSuperclass(@ForAll("thingFromSuper") Thing aString) {
				return true;
			}

			@Override
			Arbitrary<Thing> thingFromSuper() {
				return Arbitraries.constant(new Thing());
			}

			@Property
			boolean thingFromProvideInMetaAnnotation(@ForAll("thingWithMetaAnnotation") Thing aThing) {
				return true;
			}

			@MyProvide
			Arbitrary<Thing> thingWithMetaAnnotation() {
				return Arbitraries.constant(new Thing());
			}

			@Property
			boolean thingFrom(@ForAll @From("aThing") Thing t) {
				return true;
			}

			@Property
			boolean listOfThingFrom(@ForAll @Size(1) List<@From("aThing") Thing> l) {
				return true;
			}

			@Provide
			Arbitrary<Thing> aThing() {
				return Arbitraries.constant(new Thing());
			}

			@Group
			class NestedWithNamedProviders {
				@Property
				boolean nestedThingByMethodName(@ForAll("byMethodName") Thing aThing) {
					return true;
				}

				@Property
				boolean nestedThing(@ForAll("aThingByValue") Thing aThing) {
					return true;
				}

			}
		}
	}

	private static PropertyMethodArbitraryResolver getResolver(Class<?> container) {
		return new PropertyMethodArbitraryResolver(
			container,
			JqwikReflectionSupport.newInstanceWithDefaultConstructor(container),
			DomainContext.global()
		);
	}

	private static MethodParameter getParameter(Class container, String methodName) {
		return TestHelper.getParametersFor(container, methodName).get(0);
	}

}
