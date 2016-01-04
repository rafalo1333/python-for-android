
from pythonforandroid.toolchain import CythonRecipe, shprint, current_directory, info
from pythonforandroid.patching import will_build, check_any
import sh
from os.path import join


class PyjniusRecipe(CythonRecipe):
    version = 'master'
    url = 'https://github.com/kivy/pyjnius/archive/{version}.zip'
    name = 'pyjnius'
    depends = [('python2', 'python3crystax'), ('sdl2', 'sdl', 'sdl2python3crystax'), 'six']
    site_packages_name = 'jnius'

    patches = [('sdl2_jnienv_getter.patch', check_any(will_build('sdl2python3crystax'), will_build('sdl2'))),
               'getenv.patch']

    def postbuild_arch(self, arch):
        super(PyjniusRecipe, self).postbuild_arch(arch)
        info('Copying pyjnius java class to classes build dir')
        with current_directory(self.get_build_dir(arch.arch)):
            shprint(sh.cp, '-a', join('jnius', 'src', 'org'), self.ctx.javaclass_dir)


recipe = PyjniusRecipe()
