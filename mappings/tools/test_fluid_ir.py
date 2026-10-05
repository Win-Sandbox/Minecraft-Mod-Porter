# Static data regression source; intentionally not executed in this delivery.
# Usage after review: python3 -B mappings/tools/test_fluid_ir.py
import importlib.util,pathlib,unittest
P=pathlib.Path(__file__).with_name('validate_mappings.py')
spec=importlib.util.spec_from_file_location('validator',P)
v=importlib.util.module_from_spec(spec);spec.loader.exec_module(v)
H='fabric.client.FluidRenderHandler';O='fabric.client.FluidRenderHandlerRegistry';N='fabric.client.FluidRenderingRegistry'
class FluidIRTests(unittest.TestCase):
 def test_all_effective_versions(self):
  r=v.Repo()
  self.assertEqual(len(r.names('fabric')),39)
  for name in r.names('fabric'):
   with self.subTest(version=name):
    d=r.load('fabric',name)
    self.assertTrue(d.classes[H]['name'].endswith('.FluidRenderHandler'))
    modern=d.info['mcVersion'].startswith('26.')
    self.assertIn(N if modern else O,d.classes)
    self.assertNotIn(O if modern else N,d.classes)
    self.assertIn(O if modern else N,d.guidance)
    self.assertNotIn(d.classes[N if modern else O]['name'],d.removed_classes)
 def test_four_predicates_preserved(self):
  r=v.Repo()
  for loader in ('fabric','neoforge'):
   d=r.load(loader,'26.2')
   for name in ('FoodPredicate','InputPredicate','DataComponentMatchers','entity.SheepPredicate'):
    self.assertIn('mc.advancements.predicates.'+name,d.classes)
 def test_no_unsafe_member_alias(self):
  r=v.Repo()
  for name in r.names('fabric'):
   d=r.load('fabric',name)
   for ir in (H,O,N): self.assertNotIn(ir,d.members)
if __name__=='__main__': unittest.main()
