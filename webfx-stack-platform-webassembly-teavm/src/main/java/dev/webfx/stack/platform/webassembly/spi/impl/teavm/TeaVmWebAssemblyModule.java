package dev.webfx.stack.platform.webassembly.spi.impl.teavm;

import dev.webfx.stack.platform.webassembly.WebAssemblyImport;
import dev.webfx.stack.platform.webassembly.WebAssemblyInstance;
import dev.webfx.stack.platform.webassembly.WebAssemblyModule;
import dev.webfx.stack.platform.json.Json;
import dev.webfx.stack.platform.json.WritableJsonObject;
import dev.webfx.stack.async.Future;
import dev.webfx.stack.async.Promise;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSFunctor;
import org.teavm.jso.JSObject;

/**
 * @author Bruno Salmon
 */
final class TeaVmWebAssemblyModule implements WebAssemblyModule {

    private final JSObject jsModule;

    TeaVmWebAssemblyModule(JSObject jsModule) {
        this.jsModule = jsModule;
    }

    @Override
    public Future<WebAssemblyInstance> instantiate(WebAssemblyImport... imports) {
        Promise<WebAssemblyInstance> promise = Promise.promise();
        WritableJsonObject json = Json.createObject();
        for (WebAssemblyImport i : imports) {
            WritableJsonObject mod = (WritableJsonObject) json.getObject(i.getModuleName());
            if (mod == null)
                json.set(i.getModuleName(), mod = Json.createObject());
            BiIntHandler ih = (x, count) -> i.getMethod().handle(x, count);
            setImportFunction((JSObject) mod.getNativeElement(), i.getFunctionName(), ih);
        }
        instantiateModule(jsModule, (JSObject) json.getNativeElement(), instance -> promise.complete(new TeaVmWebAssemblyInstance(instance)), this::putwchar);
        return promise.future();
    }

    @JSBody(params = {"mod", "fname", "fn"}, script = "mod[fname] = fn")
    private static native void setImportFunction(JSObject mod, String fname, BiIntHandler fn);

    private StringBuffer sb = new StringBuffer();

    private void putwchar(int charCode) {
        if (charCode != 10)
            sb.append(charCodeToString(charCode));
        else {
            System.out.println(sb.toString());
            sb = new StringBuffer();
        }
    }

    @JSBody(params = {"charCode"}, script = "return String.fromCharCode(charCode)")
    private static native String charCodeToString(int charCode);

    @JSBody(params = {"module", "imports", "instanceHandler", "putwchar"}, script = "\n" +
            "  WebAssembly.instantiate(module, Object.assign(imports, {teavm : {\n" +
            "            currentTimeMillis: function() { return new Date().getTime(); },\n" +
            "            isnan: isNaN,\n" +
            "            teavm_getNaN: function() { return NaN; },\n" +
            "            isinf: function(n) { return !isFinite(n) },\n" +
            "            isfinite: isFinite,\n" +
            "            putwchar: putwchar,\n" +
            "            towlower: function (code) { return String.fromCharCode(code).toLowerCase().charCodeAt(0); },\n" +
            "            towupper: function (code) { return String.fromCharCode(code).toUpperCase().charCodeAt(0); },\n" +
            "            getNativeOffset: function (instant) { return new Date(instant).getTimezoneOffset(); },\n" +
            "            logString: console.log,\n" +
            "            logInt: console.log,\n" +
            "            logOutOfMemory: function() { console.log(\"Out of memory\") }\n" +
            "        }, teavmMath: {\n" +
            "            sqrt: Math.sqrt,\n" +
            "            pow: Math.pow,\n" +
            "            sin: Math.sin,\n" +
            "            cos: Math.cos,\n" +
            "        }}))\n" +
            ".then( function(instance) { return instanceHandler(instance) } )")
    private static native void instantiateModule(JSObject module, JSObject imports, JSObjectHandler instanceHandler, IntHandler putwchar);

    @JSFunctor
    private interface JSObjectHandler extends JSObject {
        void handle(JSObject jso);
    }

    @JSFunctor
    private interface IntHandler extends JSObject {
        void handle(int c);
    }

    @JSFunctor
    private interface BiIntHandler extends JSObject {
        void apply(int x, int count);
    }
}
