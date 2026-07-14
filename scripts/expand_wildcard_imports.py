#!/usr/bin/env python3
"""Expand wildcard imports in Java files."""

import re
import os
import sys

PACKAGE_CLASSES = {
    'java.util': [
        'ArrayList', 'Arrays', 'Calendar', 'Collection', 'Collections', 'Comparator',
        'Date', 'Deque', 'HashMap', 'HashSet', 'Hashtable', 'IdentityHashMap',
        'Iterator', 'LinkedHashMap', 'LinkedHashSet', 'LinkedList', 'List',
        'ListIterator', 'Locale', 'Map', 'NavigableMap', 'NavigableSet',
        'Objects', 'Optional', 'OptionalDouble', 'OptionalInt', 'OptionalLong',
        'Properties', 'Queue', 'Random', 'RandomAccess', 'Scanner', 'Set',
        'SortedMap', 'SortedSet', 'Stack', 'StringTokenizer', 'TreeMap',
        'TreeSet', 'UUID', 'Vector', 'WeakHashMap', 'BitSet', 'Dictionary',
        'Enumeration', 'Formatter', 'Currency', 'TimeZone', 'SimpleTimeZone',
        'GregorianCalendar', 'ServiceLoader', 'Spliterator', 'Spliterators',
        'PrimitiveIterator', 'StringJoiner', 'Observable', 'Observer',
        'AbstractCollection', 'AbstractList', 'AbstractMap', 'AbstractQueue',
        'AbstractSequentialList', 'AbstractSet', 'ArrayDeque', 'Base64',
        'EventListener', 'EventObject', 'PriorityQueue', 'PropertyPermission',
        'ResourceBundle', 'Timer', 'TimerTask', 'Properties', 'ConcurrentModificationException',
        'NoSuchElementException', 'EmptyStackException', 'TooManyListenersException',
        'InvalidPropertiesFormatException', 'DuplicateFormatFlagsException',
        'FormatFlagsConversionMismatchException', 'FormatterClosedException',
        'IllegalFormatCodePointException', 'IllegalFormatConversionException',
        'IllegalFormatException', 'IllegalFormatFlagsException',
        'IllegalFormatPrecisionException', 'IllegalFormatWidthException',
        'InputMismatchException', 'MissingFormatArgumentException',
        'MissingFormatWidthException', 'MissingResourceException',
        'NoSuchElementException', 'UnknownFormatConversionException',
        'UnknownFormatFlagsException', 'HashMap.Node', 'LinkedHashMap.Entry',
    ],
    'java.io': [
        'BufferedInputStream', 'BufferedOutputStream', 'BufferedReader', 'BufferedWriter',
        'ByteArrayInputStream', 'ByteArrayOutputStream', 'CharArrayReader', 'CharArrayWriter',
        'Closeable', 'DataInput', 'DataInputStream', 'DataOutput', 'DataOutputStream',
        'EOFException', 'Externalizable', 'File', 'FileDescriptor', 'FileFilter',
        'FileInputStream', 'FileNotFoundException', 'FileOutputStream', 'FilePermission',
        'FileReader', 'FileWriter', 'FilenameFilter', 'FilterInputStream', 'FilterOutputStream',
        'FilterReader', 'FilterWriter', 'Flushable', 'IOException', 'InputStream',
        'InputStreamReader', 'InterruptedIOException', 'InvalidClassException',
        'InvalidObjectException', 'LineNumberInputStream', 'LineNumberReader',
        'NotActiveException', 'NotSerializableException', 'ObjectInput', 'ObjectInputStream',
        'ObjectInputValidation', 'ObjectOutput', 'ObjectOutputStream', 'ObjectStreamClass',
        'ObjectStreamConstants', 'ObjectStreamException', 'OptionalDataException',
        'OutputStream', 'OutputStreamWriter', 'PipedInputStream', 'PipedOutputStream',
        'PipedReader', 'PipedWriter', 'PrintStream', 'PrintWriter', 'PushbackInputStream',
        'PushbackReader', 'RandomAccessFile', 'Reader', 'SequenceInputStream',
        'Serializable', 'SerializablePermission', 'StreamCorruptedException',
        'StreamTokenizer', 'StringBufferInputStream', 'StringReader', 'StringWriter',
        'SyncFailedException', 'UTFDataFormatException', 'UnsupportedEncodingException',
        'WriteAbortedException', 'Writer', 'Console', 'FileFilter',
        'InvalidObjectException', 'NotSerializableException',
        'InterruptedIOException', 'CharConversionException',
        'UncheckedIOException', 'IOError',
    ],
    'java.util.concurrent': [
        'AbstractExecutorService', 'ArrayBlockingQueue', 'BlockingDeque', 'BlockingQueue',
        'BrokenBarrierException', 'Callable', 'CancellationException', 'CompletableFuture',
        'CompletionException', 'CompletionService', 'ConcurrentHashMap', 'ConcurrentLinkedDeque',
        'ConcurrentLinkedQueue', 'ConcurrentMap', 'ConcurrentNavigableMap',
        'ConcurrentSkipListMap', 'ConcurrentSkipListSet', 'CopyOnWriteArrayList',
        'CopyOnWriteArraySet', 'CountDownLatch', 'CountedCompleter', 'CyclicBarrier',
        'DelayQueue', 'Delayed', 'Exchanger', 'ExecutionException', 'Executor',
        'ExecutorCompletionService', 'ExecutorService', 'Executors', 'ForkJoinPool',
        'ForkJoinTask', 'Future', 'FutureTask', 'LinkedBlockingDeque', 'LinkedBlockingQueue',
        'LinkedTransferQueue', 'Phaser', 'PriorityBlockingQueue', 'RecursiveAction',
        'RecursiveTask', 'RejectedExecutionException', 'RejectedExecutionHandler',
        'RunnableFuture', 'RunnableScheduledFuture', 'ScheduledExecutorService',
        'ScheduledFuture', 'ScheduledThreadPoolExecutor', 'Semaphore', 'SynchronousQueue',
        'ThreadFactory', 'ThreadPoolExecutor', 'ThreadLocalRandom', 'TimeoutException',
        'TimeUnit', 'TransferQueue', 'Flow', 'SubmissionPublisher',
        'RecursiveTask', 'RunnableFuture', 'RunnableScheduledFuture',
    ],
    'java.util.function': [
        'BiConsumer', 'BiFunction', 'BinaryOperator', 'BiPredicate', 'BooleanSupplier',
        'Consumer', 'DoubleBinaryOperator', 'DoubleConsumer', 'DoubleFunction',
        'DoublePredicate', 'DoubleSupplier', 'DoubleToIntFunction', 'DoubleToLongFunction',
        'DoubleUnaryOperator', 'Function', 'IntBinaryOperator', 'IntConsumer',
        'IntFunction', 'IntPredicate', 'IntSupplier', 'IntToDoubleFunction',
        'IntToLongFunction', 'IntUnaryOperator', 'LongBinaryOperator', 'LongConsumer',
        'LongFunction', 'LongPredicate', 'LongSupplier', 'LongToDoubleFunction',
        'LongToIntFunction', 'LongUnaryOperator', 'ObjDoubleConsumer', 'ObjIntConsumer',
        'ObjLongConsumer', 'Predicate', 'Supplier', 'ToDoubleBiFunction', 'ToDoubleFunction',
        'ToIntBiFunction', 'ToIntFunction', 'ToLongBiFunction', 'ToLongFunction',
        'UnaryOperator',
    ],
    'java.time': [
        'Clock', 'DayOfWeek', 'Duration', 'Instant', 'LocalDate', 'LocalDateTime',
        'LocalTime', 'Month', 'MonthDay', 'OffsetDateTime', 'OffsetTime', 'Period',
        'Year', 'YearMonth', 'ZonedDateTime', 'ZoneId', 'ZoneOffset', 'ZoneRegion',
        'Temporal', 'TemporalAccessor', 'TemporalAdjuster', 'TemporalAdjusters',
        'TemporalAmount', 'TemporalField', 'TemporalQuery', 'TemporalUnit',
        'ValueRange', 'WeekFields', 'DateTimeException', 'ChronoField', 'ChronoUnit',
        'TemporalQueries', 'FormatStyle', 'DateTimeFormatter', 'DateTimeFormatterBuilder',
        'ZoneId',
    ],
    'java.nio.file': [
        'AccessMode', 'CopyOption', 'DirectoryStream', 'FileAlreadyExistsException',
        'FileStore', 'FileSystem', 'FileSystems', 'FileVisitor', 'FileVisitOption',
        'FileVisitResult', 'Files', 'LinkOption', 'LinkPermission', 'NoSuchFileException',
        'NotDirectoryException', 'OpenOption', 'Path', 'PathMatcher', 'Paths',
        'PosixFilePermission', 'PosixFilePermissions', 'ProviderMismatchException',
        'ProviderNotFoundException', 'ReadOnlyFileSystemException', 'StandardCopyOption',
        'StandardOpenOption', 'WatchEvent', 'WatchKey', 'WatchService',
        'FileSystemException', 'FileChannel', 'StandardWatchEventKinds',
        'FileSystemAlreadyExistsException', 'InvalidPathException',
        'ProviderMismatchException', 'ClosedDirectoryStreamException',
        'ClosedFileSystemException', 'ClosedWatchServiceException',
        'FileTreeWalker', 'FileVisitResult',
    ],
    'java.awt': [
        'AlphaComposite', 'BasicStroke', 'BorderLayout', 'Button', 'Canvas', 'CardLayout',
        'Checkbox', 'CheckboxGroup', 'CheckboxMenuItem', 'Choice', 'Color', 'Component',
        'ComponentOrientation', 'Container', 'Cursor', 'Desktop', 'Dialog', 'Dimension',
        'DisplayMode', 'Event', 'EventQueue', 'FileDialog', 'FlowLayout',
        'FocusTraversalPolicy', 'Font', 'FontFormatException', 'FontMetrics', 'Frame',
        'GradientPaint', 'Graphics', 'Graphics2D', 'GraphicsConfiguration',
        'GraphicsDevice', 'GraphicsEnvironment', 'GridBagConstraints', 'GridBagLayout',
        'GridLayout', 'Image', 'Insets', 'JobAttributes', 'Label', 'List',
        'MediaTracker', 'Menu', 'MenuBar', 'MenuComponent', 'MenuItem', 'MenuShortcut',
        'MouseInfo', 'PageAttributes', 'Panel', 'Point', 'Polygon', 'PopupMenu',
        'PrintGraphics', 'PrintJob', 'Rectangle', 'RenderingHints', 'Robot',
        'ScrollPane', 'Scrollbar', 'ScrollPaneAdjustable', 'SystemColor',
        'TextArea', 'TextComponent', 'TextField', 'TexturePaint', 'Toolkit',
        'Transparency', 'Window', 'AWTError', 'AWTEvent', 'AWTEventMulticaster',
        'AWTException', 'AWTKeyStroke', 'AWTPermission', 'HeadlessException',
        'IllegalComponentStateException', 'Graphics2D', 'Font', 'Color', 'BasicStroke',
        'RenderingHints', 'AlphaComposite', 'GradientPaint', 'TexturePaint',
    ],
    'java.util.zip': [
        'Adler32', 'CRC32', 'CheckedInputStream', 'CheckedOutputStream', 'Checksum',
        'DataFormatException', 'Deflater', 'DeflaterInputStream', 'DeflaterOutputStream',
        'GZIPInputStream', 'GZIPOutputStream', 'Inflater', 'InflaterInputStream',
        'InflaterOutputStream', 'ZipEntry', 'ZipException', 'ZipFile', 'ZipInputStream',
        'ZipOutputStream', 'ZipError', 'Deflater', 'Inflater',
    ],
    'okhttp3': [
        'Authenticator', 'Cache', 'CacheControl', 'Call', 'Callback', 'CertificatePinner',
        'Challenge', 'CipherSuite', 'Connection', 'ConnectionPool', 'ConnectionSpec',
        'Cookie', 'CookieJar', 'Credentials', 'Dispatcher', 'Dns', 'EventListener',
        'FormBody', 'Handshake', 'Headers', 'HttpUrl', 'Interceptor', 'MediaType',
        'MultipartBody', 'OkHttpClient', 'Protocol', 'RealCall', 'Request', 'RequestBody',
        'Response', 'ResponseBody', 'Route', 'TlsVersion', 'WebSocket', 'WebSocketListener',
        'Address', 'CacheStrategy', 'ConnectionListener',
        'MultipartBody.Part', 'MultipartBody.Builder', 'FormBody.Builder',
        'OkHttpClient.Builder', 'Request.Builder', 'RequestBody.Companion',
        'HttpUrl.Builder', 'Headers.Builder', 'MediaType.Companion',
        'Response.Builder', 'CacheControl.Builder', 'ConnectionSpec.Builder',
    ],
    'java.sql': [
        'Array', 'BatchUpdateException', 'Blob', 'CallableStatement', 'Clob',
        'Connection', 'DatabaseMetaData', 'Date', 'Driver', 'DriverManager',
        'DriverPropertyInfo', 'NClob', 'ParameterMetaData', 'PreparedStatement',
        'Ref', 'ResultSet', 'ResultSetMetaData', 'RowId', 'RowIdLifetime',
        'Savepoint', 'SQLException', 'SQLFeatureNotSupportedException',
        'SQLPermission', 'SQLWarning', 'SQLData', 'SQLInput', 'SQLOutput',
        'SQLType', 'Struct', 'Time', 'Timestamp', 'Types', 'Wrapper',
        'ConnectionBuilder', 'ShardingKey', 'ShardingKeyBuilder',
    ],
    'javax.crypto': [
        'AEADBadTagException', 'BadPaddingException', 'Cipher', 'CipherInputStream',
        'CipherOutputStream', 'CipherSpi', 'DecapsulateException',
        'EncryptedPrivateKeyInfo', 'ExemptionMechanism',
        'ExemptionMechanismException', 'ExemptionMechanismSpi',
        'IllegalBlockSizeException', 'KeyAgreement', 'KeyAgreementSpi',
        'KeyGenerator', 'KeyGeneratorSpi', 'Mac', 'MacSpi',
        'NoSuchPaddingException', 'NullCipher', 'SealedObject', 'SecretKey',
        'SecretKeyFactory', 'SecretKeyFactorySpi', 'ShortBufferException',
        'KEM', 'KEMSpi',
    ],
}


def expand_wildcard_imports(filepath):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()

    lines = content.split('\n')
    new_lines = []
    body = content

    changed = False

    for line in lines:
        stripped = line.strip()
        match = re.match(r'^import\s+(static\s+)?([\w.]+)\.\*;\s*$', stripped)
        if match:
            is_static = match.group(1) is not None
            package = match.group(2)

            if package not in PACKAGE_CLASSES:
                new_lines.append(line)
                continue

            used_classes = set()
            for class_name in PACKAGE_CLASSES[package]:
                pattern = r'(?<![\w.])' + re.escape(class_name) + r'(?![\w])'
                if re.search(pattern, body):
                    used_classes.add(class_name)

            if not used_classes:
                changed = True
                continue

            for class_name in sorted(used_classes):
                prefix = 'import static ' if is_static else 'import '
                new_lines.append(f'{prefix}{package}.{class_name};')
            changed = True
        else:
            new_lines.append(line)

    if changed:
        new_content = '\n'.join(new_lines)
        with open(filepath, 'w', encoding='utf-8', newline='\n') as f:
            f.write(new_content)
        return True
    return False


def main():
    base = sys.argv[1] if len(sys.argv) > 1 else '.'
    count = 0
    for root, dirs, files in os.walk(base):
        for fname in files:
            if fname.endswith('.java'):
                filepath = os.path.join(root, fname)
                if expand_wildcard_imports(filepath):
                    count += 1
                    print(f'Expanded: {os.path.relpath(filepath, base)}')
    print(f'\nTotal files processed: {count}')


if __name__ == '__main__':
    main()
