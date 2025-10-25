[Arc framework](github.com/Anuken/Arc/) without client related things and with bug fixes and new features.

Intended use is to be included in standalone server applications as a small and optimized utility framework.


## Usage
First, add our repository to your gradle build script:
```gradle
repositories {
  maven { url 'https://maven.xpdustry.com/releases' }
}
```
Add the framework to your project dependencies *(the versioning is the same as Arc)*:
```gradle
dependencies {
  implementation 'com.xpdustry:arc-lite:v152.2'
}
```
And you can use the API the same way as Arc. The main package is ``arc``.

----

Optionally, to be able to get colors on Windows consoles *(e.g. the cmd)*, you'll need to include some libraries to your project dependencies:
```gradle
dependencies {
  implementation "org.fusesource.jansi:jansi-native:1.1"
  implementation "org.fusesource.jansi:jansi-native:1.1:windows32"
  implementation "org.fusesource.jansi:jansi-native:1.1:windows64"
}
```

It's also recommended to *"minimize"* your project using the [gradleup's shadow plugin](https://gradleup.com/shadow/getting-started/). <br>
This will remove everything that is not used by your project, making it smaller.


## Changes
Modified source structure to match a classic Gradle project. <br>
Removed [``natives/``](https://github.com/Anuken/Arc/tree/master/natives) libraries.
<details>
  <summary><strong>Removed backends</strong></summary>
  <ul>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/backends/backend-android">arc.backend.android</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/backends/backend-robovm">arc.backend.robovm</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/backends/backend-sdl">arc.backend.sdl</a></code></li>
  </ul>
</details>

Merged [headless backend](https://github.com/Anuken/Arc/tree/master/backends/backend-headless) to main source.

<details>
  <summary><strong>Removed extensions</strong></summary>
  <ul>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/extensions/box2d">arc.box2d</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/extensions/discord">arc.discord</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/extensions/filedialogs">arc.filedialogs</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/extensions/flabel">arc.flabel</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/extensions/freetpye">arc.freetype</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/extensions/fx">arc.fx</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/extensions/g3d">arc.g3d</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/extensions/packer">arc.packer</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/extensions/profiling">arc.profiling</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/extensions/recorder">arc.gif</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/extensions/tiled">arc.maps</a></code></li>
  </ul>
</details>

Merged [arcnet extension](https://github.com/Anuken/Arc/tree/master/extensions/arcnet) to main source.

<details>
  <summary><strong>Removed packages and classes</strong></summary>
  <ul>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/arc-core/src/arc/Input.java">arc.Input</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/arc-core/src/arc/Graphics.java">arc.Graphics</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/arc-core/src/arc/assets">arc.assets</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/arc-core/src/arc/audio">arc.audio</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/Blending.java">arc.graphics.Blending</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/Camera.java">arc.graphics.Camera</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/Cubemap.java">arc.graphics.Cubemap</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/CubemapData.java">arc.graphics.CubemapData</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/GL20.java">arc.graphics.GL20</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/GL30.java">arc.graphics.GL30</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/GLTexture.java">arc.graphics.GLTexture</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/Gl.java">arc.graphics.Gl</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/Mesh.java">arc.graphics.Mesh</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/Texture.java">arc.graphics.Texture</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/TextureArray.java">arc.graphics.TextureArray</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/TextureArrayData.java">arc.graphics.TextureArrayData</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/TextureData.java">arc.graphics.TextureData</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/VertexAttribute.java">arc.graphics.VertexAttribute</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/arc-core/src/arc/graphics/g2d">arc.graphics.g2d</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/arc-core/src/arc/graphics/gl">arc.graphics.gl</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/arc-core/src/arc/input">arc.input</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/mock/MockAudio.java">arc.mock.MockAudio</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/mock/MockGL20.java">arc.mock.MockGL20</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/mock/MockGraphics.java">arc.mock.MockGraphics</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/mock/MockInput.java">arc.mock.MockInput</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/arc-core/src/arc/scene">arc.scene</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/arc-core/src/arc/util/ScreenRecorder.java">arc.util.ScreenRecorder</a></code></li>
      <li><code><a href="https://github.com/Anuken/Arc/tree/master/arc-core/src/arc/util/ScreenUtils.java">arc.util.ScreenUtils</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/tree/master/arc-core/src/arc/util/viewport">arc.util.viewport</a></code></li>
  </ul>
</details>

Moved ``arc.graphics.g2d.PixmapRegion`` to ``arc.graphics``.

<details>
  <summary><strong>Removed classes warnings</strong></summary>
  <ul>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/Events.java">arc.Events</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/Files.java">arc.Files</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/files/Fi.java">arc.files.Fi</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/math/Rand.java">arc.math.Rand</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/math/geom/Bezier.java">arc.math.geom.Bezier</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/math/geom/BSpline.java">arc.math.geom.BSpline</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/math/geom/CatmullRomSpline.java">arc.math.geom.CatmullRomSpline</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/math/geom/ConvexHull.java">arc.math.geom.ConvexHull</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/math/geom/Point3.java">arc.math.geom.Point3</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/net/ArcNetException.java">arc.net.ArcNetException</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/net/Server.java">arc.net.Server</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/struct">arc.struct.*</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/files/ZipFi.java">arc.files.ZipFi</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/CommandHandler.java">arc.util.CommandHandler</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/Http/HttpStatusException.java">arc.util.Http.HttpStatusException</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/Select.java">arc.util.Select</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/SharedLibraryLoader.java">arc.util.SharedLibraryLoader</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/Structs.java">arc.util.Structs</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/io/CRC.java">arc.util.io.CRC</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/pooling/Pool.java">arc.util.pooling.Pool</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/pooling/Pools.java">arc.util.pooling.Pools</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/serialization/Json.java">arc.util.serialization.Json</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/ arc/util/serialization/JsonReadear.java">arc.util.serialization.JsonReadear</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/serialization/JsonValue.java">arc.util.serialization.JsonValue</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/serialization/JsonWriter.java">arc.util.serialization.JsonWriter</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/serialization/Jval.java">arc.util.serialization.Jval</a></code></li>
    <li><code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/serialization/SerializationException.java">arc.util.serialization.SerializationException</a></code></li>
  </ul>
</details> 

<details>
  <summary><strong>Modified classes</strong></summary>
  <ul>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/Application.java">arc.Application</a></code>: moved delta and frame rate calculation from <code><a href="https://github.com/Anuken/Arc/tree/master/arc-core/src/arc/Graphics.java">arc.Graphics</a></code> because <code><a href="https://github.com/Anuken/Arc/tree/master/arc-core/src/arc/util/Time.java">arc.util.Time</a></code> depends on them</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/Core.java">arc.Core</a></code>: removed client related fields</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/backend/headless/HeadlessApplication.java">arc.backend.headless.HeadlessApplication</a></code>: removed client related fields, moved delta and frame rate calculation from <code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/mock/MockGraphics.java">arc.mock.MockGraphics</a></code></li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/graphics/Pixmap.java">arc.graphics.Pixmap</a></code>: moved used <code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/graphics/Gl.java">arc.graphics.Gl</a></code> fields to a private class</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/graphics/Pixmaps.java">arc.graphics.Pixmaps</a></code>: removed Texture related methods</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/math/Angles.java">arc.math.Angles</a></code>: removed <code>#mouseAngle()</code></li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/math/Mathf.java">arc.math.Mathf</a></code>: removed unnecessary <code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/math/geom/Vec2.java">Vec2</a></code></li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/math/geom/QuadTree.java">arc.math.geom.QuadTree</a></code>: reduced intersect methods</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/net/Client.java">arc.net.Client</a></code>: changed some private field to protected</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/net/Connection.java">arc.net.Connection</a></code>: changed some private field to protected</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/net/Server.java">arc.net.Server</a></code>: changed some private field to protected</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/ColorCodes.java">arc.util.ColorCodes</a></code>: moved colors application from <code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/Log.java">arc.util.Log</a></code></code></li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/Log.java">arc.util.Log</a></code>: moved colors application to <code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/ColorCodes.java">arc.util.ColorCodes</a></code></code></li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/Http.java">arc.util.Http</a></code>: added a getter for <code>HttpResponse.connection</code>, fixed <code>HttpStatus.byCode()</code> mapping, added more status in <code>HttpStatus</code></li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/Time.java">arc.util.Time</a></code>: use <code>Core.app.getDeltaTime()</code> instead of <code>Core.graphics.getDeltaTime()</code></li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/Tmp.java">arc.util.Tmp</a></code>: removed texture related things</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/serialization/JsonValue.java">arc.util.serialization.JsonValue</a></code>: fixed formatting, removed <code>PrettyPrintSettings</code> because it's useless</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/serialization/JsonWriter.java">arc.util.serialization.JsonWriter</a></code>: added serialization of a <code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/serialization/JsonValue.java">JsonValue</a></code></li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/serialization/UBJsonWriter.java">arc.util.serialization.UBJsonWriter</a></code>: fixed <code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/serialization/JsonValue.java">JsonValue</a></code> serialization via generic Object</li>
  </ul>
</details>

<details>
  <summary><strong>Added features</strong></summary>
  <ul>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/serialization/Json.java">arc.util.serialization.Json</a></code>: added getter for <code>typeName</code>, added a way to deserialize json without setting fields to an object, fixed json object key serialization, added inherited serializers, removed <code>PrettyPrintSettings</code></li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/serialization/JsonValue.java">arc.util.serialization.JsonValue</a></code>: added a <code>#copy()</code> method</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/serialization/JsonWriterBuilder.java">arc.util.serialization.JsonWriterBuilder</a></code>: added a json builder used by <code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/serialization/Json.java">Json</a></code> to convert an object to a <code><a href="https://github.com/Anuken/Arc/blob/master/arc-core/src/arc/util/serialization/JsonValue.java">JsonValue</a></code>.</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/Strings.java">arc.util.Strings</a></code>: added various methods.</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/Structs.java">arc.util.Structs</a></code>: added various methods.</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/Timekeeper.java">arc.util.Timekeeper</a></code>: added methods to know elapsed time, remaining time, etc.</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/io/ByteBufferOutput.java">arc.util.io.ByteBufferOutput</a></code>: filled stubs and suppressed IOException from <code>#writeUTF()</code></li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/io/ByteBufferInput.java">arc.util.io.ByteBufferInput</a></code>: suppressed IOException from <code>#readUTF()</code></li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/Logger.java">arc.util.Logger</a></code>: added a logger with topics</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/Autosaver.java">arc.util.Autosaver</a></code>: added an auto saver (should be started manually by the app)</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/JsonSettings.java">arc.util.JsonSettings</a></code>: added a configurable json settings handler.</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/Settings.java">arc.Settings</a></code>: just a JsonSettings with a dynamic saving directory and without the settings backup directory (compared to original arc.Settings)</li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/struct/Seq.java">arc.struct.Seq</a></code>: added features from <a href="https://github.com/mindustry-antigrief/Arc">foo's Arc</a></li>
    <li><code><a href="https://github.com/xpdustry/arc-lite/blob/master/src/main/java/arc/util/Time.java">arc.util.Time</a></code>: added features from <a href="https://github.com/mindustry-antigrief/Arc">foo's Arc</a></li>
  </ul>
</details>