/*
 * Copyright 2010 Henry Coles
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 * 
 * http://www.apache.org/licenses/LICENSE-2.0 
 * 
 * Unless required by applicable law or agreed to in writing, 
 * software distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and limitations under the License. 
 */
package org.pitest.mutationtest.engine.gregor;

import static org.pitest.functional.Prelude.and;
import static org.pitest.functional.Prelude.not;
import static org.pitest.util.Functions.classNameToJVMClassName;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.pitest.bytecode.NullVisitor;
import org.pitest.classinfo.ClassName;
import org.pitest.functional.F;
import org.pitest.functional.FCollection;
import org.pitest.functional.FunctionalList;
import org.pitest.functional.Option;
import org.pitest.functional.predicate.Predicate;
import org.pitest.internal.ClassByteArraySource;
import org.pitest.mutationtest.MutationDetails;
import org.pitest.mutationtest.engine.Mutant;
import org.pitest.mutationtest.engine.Mutater;
import org.pitest.mutationtest.engine.MutationIdentifier;

class GregorMutater implements Mutater {

  private final Predicate<MethodInfo>     filter;
  private final ClassByteArraySource      byteSource;
  private final Set<MethodMutatorFactory> mutators       = new HashSet<MethodMutatorFactory>();
  private final Set<String>               loggingClasses = new HashSet<String>();

  public GregorMutater(final ClassByteArraySource byteSource,
      final Predicate<MethodInfo> filter,
      final Collection<MethodMutatorFactory> mutators,
      final Collection<String> loggingClasses) {
    this.filter = filter;
    this.mutators.addAll(mutators);
    this.byteSource = byteSource;
    this.loggingClasses.addAll(FCollection.map(loggingClasses,
        classNameToJVMClassName()));
  }

  public FunctionalList<MutationDetails> findMutations(
      final ClassName classToMutate) {

    final Context context = new Context();
    context.setTargetMutation(Option.<MutationIdentifier> none());
    return GregorMutater.this.byteSource.apply(classToMutate.asInternalName())
        .flatMap(findMutations(context));

  }

  private F<byte[], Iterable<MutationDetails>> findMutations(
      final Context context) {
    return new F<byte[], Iterable<MutationDetails>>() {
      public Iterable<MutationDetails> apply(final byte[] bytes) {
        return findMutationsForBytes(context, bytes);
      }

    };
  }

  private Collection<MutationDetails> findMutationsForBytes(
      final Context context, final byte[] classToMutate) {

    final PremutationClassInfo classInfo = performPreScan(classToMutate);

    final ClassReader first = new ClassReader(classToMutate);
    final NullVisitor nv = new NullVisitor();
    final MutatingClassVisitor mca = new MutatingClassVisitor(nv, context,
        filterMethods(), classInfo, this.mutators);

    first.accept(mca, ClassReader.EXPAND_FRAMES);

    return context.getCollectedMutations();

  }

  private PremutationClassInfo performPreScan(final byte[] classToMutate) {
    final ClassReader reader = new ClassReader(classToMutate);

    final PreMutationAnalyser an = new PreMutationAnalyser(this.loggingClasses);
    reader.accept(an, 0);
    return an.getClassInfo();

  }

  public Mutant getMutation(final MutationIdentifier id) {

    final Context context = new Context();
    context.setTargetMutation(Option.some(id));

    final Option<byte[]> bytes = this.byteSource.apply(id.getClazz());

    final PremutationClassInfo classInfo = performPreScan(bytes.value());

    final ClassReader reader = new ClassReader(bytes.value());
    final ClassWriter w = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
    final MutatingClassVisitor mca = new MutatingClassVisitor(w, context,
        filterMethods(), classInfo, FCollection.filter(this.mutators,
            isMutatorFor(id)));
    reader.accept(mca, ClassReader.EXPAND_FRAMES);

    final FunctionalList<MutationDetails> details = context
        .getMutationDetails(context.getTargetMutation().value());

    return new Mutant(details.get(0), w.toByteArray());

  }

  private static Predicate<MethodMutatorFactory> isMutatorFor(
      final MutationIdentifier id) {
    return new Predicate<MethodMutatorFactory>() {

      public Boolean apply(final MethodMutatorFactory a) {
        return id.getMutator().equals(a.getGloballyUniqueId());
      }

    };
  }

  @SuppressWarnings("unchecked")
  private Predicate<MethodInfo> filterMethods() {
    return and(this.filter, filterSyntheticMethods(),
        not(isGeneratedEnumMethod()), not(isGroovyClass()));
  }

  private static F<MethodInfo, Boolean> isGroovyClass() {
    return new Predicate<MethodInfo>() {
      public Boolean apply(final MethodInfo a) {
        return a.isInGroovyClass();
      }

    };
  }

  private static Predicate<MethodInfo> filterSyntheticMethods() {
    return new Predicate<MethodInfo>() {

      public Boolean apply(final MethodInfo a) {
        return !a.isSynthetic();
      }

    };
  }

  private static Predicate<MethodInfo> isGeneratedEnumMethod() {
    return new Predicate<MethodInfo>() {
      public Boolean apply(final MethodInfo a) {
        return a.isGeneratedEnumMethod();
      }
    };
  }


  public byte[] getOriginalClass(final ClassName clazz) {
    return this.byteSource.apply(clazz.asInternalName()).value();
  }

}
