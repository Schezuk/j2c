# java.lang.Runtime #
Running the converter on [java/lang/Runtime.java](http://hg.openjdk.java.net/jdk6/jdk6/jdk/file/99b43838c5d0/src/share/classes/java/lang/Runtime.java) and its dependencies produces ~2000 classes (direct and indirect dependencies).

For Runtime itself, four files are generated. Had Runtime had any nested classes, separate files would have been generated for each of those.

First is the forward declaration header which contains forward declarations of the classes involved. Currently, a forward header is generated for each package containing all classes of that package.

`java.lang.Runtime.h` contains the class declaration, `java.lang.Runtime.cpp` definitions and `java.lang.Runtime-native` the blanks you'd have to fill in to make Runtime work.

Generated using version 0.2.0 of J2C.

## java/lang/Runtime.hpp ##
```
// Generated from /jdk6/java/lang/Runtime.java

#pragma once

#include "fwd.hpp"
#include "java/io.fwd.hpp"
#include "java/lang/Object.hpp"

class java::lang::Runtime
    : public virtual Object
{

public:
    typedef Object super;

private:
    static Runtime *currentRuntime__;

public:
    static Runtime *getRuntime();
protected:
    void ctor();

public:
    virtual void exit(int32_t status_);
    virtual void addShutdownHook(Thread *hook_);
    virtual bool removeShutdownHook(Thread *hook_);
    virtual void halt(int32_t status_);
    static void runFinalizersOnExit(bool value_);
    virtual Process *exec(String *command_) /* throws(IOException) */;
    virtual Process *exec(String *command_, StringArray *envp_) /* throws(IOException) */;
    virtual Process *exec(String *command_, StringArray *envp_, ::java::io::File *dir_) /* throws(IOException) */;
    virtual Process *exec(StringArray *cmdarray_) /* throws(IOException) */;
    virtual Process *exec(StringArray *cmdarray_, StringArray *envp_) /* throws(IOException) */;
    virtual Process *exec(StringArray *cmdarray_, StringArray *envp_, ::java::io::File *dir_) /* throws(IOException) */;
    virtual int32_t availableProcessors();
    virtual int64_t freeMemory();
    virtual int64_t totalMemory();
    virtual int64_t maxMemory();
    virtual void gc();

private:
    static void _runFinalization0();

public:
    virtual void runFinalization();
    virtual void traceInstructions(bool on_);
    virtual void traceMethodCalls(bool on_);
    virtual void load(String *filename_);

public: /* package */
    virtual void load0(Class *fromClass_, String *filename_);

public:
    virtual void loadLibrary(String *libname_);

public: /* package */
    virtual void loadLibrary0(Class *fromClass_, String *libname_);

public:
    virtual ::java::io::InputStream *getLocalizedInputStream(::java::io::InputStream *in_);
    virtual ::java::io::OutputStream *getLocalizedOutputStream(::java::io::OutputStream *out_);

    // Generated
protected:
    Runtime();

public:
    static ::java::lang::Class *class_();
    static void clinit();

private:
    static Runtime *&currentRuntime_();
    virtual ::java::lang::Class* getClass0();
};
```

## java.lang.Runtime.cpp ##
```
// Generated from /jdk6/java/lang/Runtime.java
#include "java/lang/Runtime.hpp"

#include "java/io/File.hpp"
#include "java/io/InputStream.hpp"
#include "java/io/OutputStream.hpp"
#include "java/lang/ApplicationShutdownHooks.hpp"
#include "java/lang/ClassLoader.hpp"
#include "java/lang/IllegalArgumentException.hpp"
#include "java/lang/Process.hpp"
#include "java/lang/ProcessBuilder.hpp"
#include "java/lang/RuntimePermission.hpp"
#include "java/lang/SecurityException.hpp"
#include "java/lang/SecurityManager.hpp"
#include "java/lang/Shutdown.hpp"
#include "java/lang/String.hpp"
#include "java/lang/StringBuilder.hpp"
#include "java/lang/System.hpp"
#include "java/lang/UnsatisfiedLinkError.hpp"
#include "java/util/StringTokenizer.hpp"
#include "java/lang/StringArray.hpp"

::java::lang::Runtime::Runtime() 
{
    clinit();
    ctor();
}

::java::lang::Runtime *&::java::lang::Runtime::currentRuntime_()
{
    clinit();
    return currentRuntime__;
}
::java::lang::Runtime *::java::lang::Runtime::currentRuntime__;

::java::lang::Runtime *::java::lang::Runtime::getRuntime()
{
    clinit();
    return Runtime::currentRuntime_();
}

void ::java::lang::Runtime::ctor()
{
}

void ::java::lang::Runtime::exit(int32_t status_)
{
    SecurityManager *security_ = System::getSecurityManager();
    if(security_ != nullptr) {
        security_->checkExit(status_);
    }
    Shutdown::exit(status_);
}

void ::java::lang::Runtime::addShutdownHook(Thread *hook_)
{
    SecurityManager *sm_ = System::getSecurityManager();
    if(sm_ != nullptr) {
        sm_->checkPermission((new RuntimePermission(u"shutdownHooks"_j)));
    }
    ApplicationShutdownHooks::add(hook_);
}

bool ::java::lang::Runtime::removeShutdownHook(Thread *hook_)
{
    SecurityManager *sm_ = System::getSecurityManager();
    if(sm_ != nullptr) {
        sm_->checkPermission((new RuntimePermission(u"shutdownHooks"_j)));
    }
    return ApplicationShutdownHooks::remove(hook_);
}

void ::java::lang::Runtime::halt(int32_t status_)
{
    SecurityManager *sm_ = System::getSecurityManager();
    if(sm_ != nullptr) {
        sm_->checkExit(status_);
    }
    Shutdown::halt(status_);
}

void ::java::lang::Runtime::runFinalizersOnExit(bool value_)
{
    clinit();
    SecurityManager *security_ = System::getSecurityManager();
    if(security_ != nullptr) {
        try {
            security_->checkExit(int32_t(0));
        } catch (SecurityException *e_) {
            throw (new SecurityException(u"runFinalizersOnExit"_j));
        }
    }
    Shutdown::setRunFinalizersOnExit(value_);
}

::java::lang::Process *::java::lang::Runtime::exec(String *command_) /* throws(IOException) */
{
    return exec(command_, static_cast< StringArray* >(nullptr), static_cast< ::java::io::File* >(nullptr));
}

::java::lang::Process *::java::lang::Runtime::exec(String *command_, StringArray *envp_) /* throws(IOException) */
{
    return exec(command_, envp_, static_cast< ::java::io::File* >(nullptr));
}

::java::lang::Process *::java::lang::Runtime::exec(String *command_, StringArray *envp_, ::java::io::File *dir_) /* throws(IOException) */
{
    if(command_->length() == int32_t(0))
        throw (new IllegalArgumentException(u"Empty command"_j));

    ::java::util::StringTokenizer *st_ = (new ::java::util::StringTokenizer(command_));
    StringArray *cmdarray_ = (new StringArray(st_->countTokens()));
    for (int32_t  i_ = int32_t(0); st_->hasMoreTokens(); i_++) 
                cmdarray_->set(i_, st_->nextToken());

    return exec(cmdarray_, envp_, dir_);
}

::java::lang::Process *::java::lang::Runtime::exec(StringArray *cmdarray_) /* throws(IOException) */
{
    return exec(cmdarray_, static_cast< StringArray* >(nullptr), static_cast< ::java::io::File* >(nullptr));
}

::java::lang::Process *::java::lang::Runtime::exec(StringArray *cmdarray_, StringArray *envp_) /* throws(IOException) */
{
    return exec(cmdarray_, envp_, static_cast< ::java::io::File* >(nullptr));
}

::java::lang::Process *::java::lang::Runtime::exec(StringArray *cmdarray_, StringArray *envp_, ::java::io::File *dir_) /* throws(IOException) */
{
    return (new ProcessBuilder(cmdarray_))->environment(envp_)->directory(dir_)->start();
}

void ::java::lang::Runtime::runFinalization()
{
    _runFinalization0();
}

void ::java::lang::Runtime::load(String *filename_)
{
    load0(System::getCallerClass(), filename_);
}

void ::java::lang::Runtime::load0(Class *fromClass_, String *filename_)
{
    SecurityManager *security_ = System::getSecurityManager();
    if(security_ != nullptr) {
        security_->checkLink(filename_);
    }
    if(!((new ::java::io::File(filename_))->isAbsolute())) {
        throw (new UnsatisfiedLinkError((new ::java::lang::StringBuilder())->append(u"Expecting an absolute path of the library: "_j)->append(filename_)->toString()));
    }
    ClassLoader::loadLibrary(fromClass_, filename_, true);
}

void ::java::lang::Runtime::loadLibrary(String *libname_)
{
    loadLibrary0(System::getCallerClass(), libname_);
}

void ::java::lang::Runtime::loadLibrary0(Class *fromClass_, String *libname_)
{
    SecurityManager *security_ = System::getSecurityManager();
    if(security_ != nullptr) {
        security_->checkLink(libname_);
    }
    if(libname_->indexOf(static_cast< int32_t >(::java::io::File::separatorChar_())) != -int32_t(1)) {
        throw (new UnsatisfiedLinkError((new ::java::lang::StringBuilder())->append(u"Directory separator should not appear in library name: "_j)->append(libname_)->toString()));
    }
    ClassLoader::loadLibrary(fromClass_, libname_, false);
}

::java::io::InputStream *::java::lang::Runtime::getLocalizedInputStream(::java::io::InputStream *in_)
{
    return in_;
}

::java::io::OutputStream *::java::lang::Runtime::getLocalizedOutputStream(::java::io::OutputStream *out_)
{
    return out_;
}

extern ::java::lang::Class *class_(const char16_t *c, int n);

::java::lang::Class *::java::lang::Runtime::class_()
{
    static ::java::lang::Class *c = ::class_(u"java.lang.Runtime", 17);
    return c;
}

static bool clinit_done;
void ::java::lang::Runtime::clinit()
{
    super::clinit();
    if(!::clinit_done) {
        ::clinit_done = true;
    currentRuntime_() = (new Runtime());
}
}

::java::lang::Class *::java::lang::Runtime::getClass0()
{
    return class_();
}

```

## java/lang/Runtime-native.cpp ##
```
// Generated from /jdk6/java/lang/Runtime.java
#include "java/lang/Runtime.hpp"


int32_t ::java::lang::Runtime::availableProcessors()
{ /* native */
    return 0;
}

int64_t ::java::lang::Runtime::freeMemory()
{ /* native */
    return 0;
}

void ::java::lang::Runtime::gc()
{ /* native */
}

int64_t ::java::lang::Runtime::maxMemory()
{ /* native */
    return 0;
}

void ::java::lang::Runtime::_runFinalization0()
{ /* native */
    clinit();
}

int64_t ::java::lang::Runtime::totalMemory()
{ /* native */
    return 0;
}

void ::java::lang::Runtime::traceInstructions(bool on_)
{ /* native */
}

void ::java::lang::Runtime::traceMethodCalls(bool on_)
{ /* native */
}


int64_t ::java::lang::Runtime::totalMemory()
{ /* native */
    return 0;
}

void ::java::lang::Runtime::traceInstructions(bool on_)
{ /* native */
}

void ::java::lang::Runtime::traceMethodCalls(bool on_)
{ /* native */
}
```