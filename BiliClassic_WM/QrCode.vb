' QR Code generator (VB port for .NET CF 2.0)
'
' Ported from Project Nayuki's qrcodegen Java library:
'   Copyright (c) Project Nayuki. (MIT License)
'   https://www.nayuki.io/page/qr-code-generator-library
'
' Self-contained: no external data files (GF(2^8) arithmetic is computed at
' runtime), so it runs on .NET Compact Framework 2.0.
'
' Usage (high level):
'   Dim bmp As Bitmap = QrCode.MakeQrBitmap(url, QrCode.ECC_LOW, 4, 2)

Public Enum QrEcc As Integer
    ECC_LOW = 0
    ECC_MEDIUM = 1
    ECC_QUARTILE = 2
    ECC_HIGH = 3
End Enum

Public Class QrCode

    Public Const MIN_VERSION As Integer = 1
    Public Const MAX_VERSION As Integer = 40

    Private Shared Function GetFormatBits(ByVal ecl As QrEcc) As Integer
        Select Case ecl
            Case QrEcc.ECC_LOW
                Return 1
            Case QrEcc.ECC_MEDIUM
                Return 0
            Case QrEcc.ECC_QUARTILE
                Return 3
            Case Else
                Return 2
        End Select
    End Function

    ' High level: encodes an ASCII/UTF-8 string in byte mode and returns a
    ' System.Drawing.Bitmap with the given module scale and quiet-zone border.
    Public Shared Function MakeQrBitmap(ByVal text As String, ByVal ecl As QrEcc, ByVal scale As Integer, ByVal border As Integer) As System.Drawing.Bitmap
        Dim bytes As Byte() = System.Text.Encoding.UTF8.GetBytes(text)
        Dim qr As QrCode = EncodeBytes(bytes, ecl)
        Return qr.RenderToBitmap(scale, border)
    End Function

    ' High level: encodes binary data in byte mode, auto-choosing version & mask.
    Public Shared Function EncodeBytes(ByVal data As Byte(), ByVal ecl As QrEcc) As QrCode
        Dim bb As New BitBuffer()
        For i As Integer = 0 To data.Length - 1
            bb.AppendBits(data(i), 8)
        Next
        Dim segs As New System.Collections.ArrayList()
        segs.Add(New QrSegment(QrSegment.MODE_BYTE, data.Length, bb))
        Return EncodeSegments(segs, ecl, MIN_VERSION, MAX_VERSION, -1, True)
    End Function

    ' Mid level: encodes the given segments, auto-choosing version & (optionally) mask.
    Public Shared Function EncodeSegments(ByVal segs As System.Collections.ArrayList, ByVal ecl As QrEcc, ByVal minVersion As Integer, ByVal maxVersion As Integer, ByVal mask As Integer, ByVal boostEcl As Boolean) As QrCode
        If minVersion < MIN_VERSION OrElse minVersion > maxVersion OrElse maxVersion > MAX_VERSION OrElse mask < -1 OrElse mask > 7 Then
            Throw New ArgumentException("Invalid value")
        End If

        ' Find the minimal version number to use
        Dim version As Integer = minVersion
        Dim dataUsedBits As Integer = 0
        While True
            Dim dataCapacityBits As Integer = GetNumDataCodewords(version, ecl) * 8
            dataUsedBits = QrSegment.GetTotalBits(segs, version)
            If dataUsedBits <> -1 AndAlso dataUsedBits <= dataCapacityBits Then
                Exit While
            End If
            If version >= maxVersion Then
                Throw New Exception("Data length = " & dataUsedBits.ToString() & " bits, Max capacity = " & dataCapacityBits.ToString() & " bits")
            End If
            version += 1
        End While

        ' Increase the error correction level while the data still fits (low to high)
        If boostEcl Then
            Dim levels As QrEcc() = New QrEcc() {QrEcc.ECC_LOW, QrEcc.ECC_MEDIUM, QrEcc.ECC_QUARTILE, QrEcc.ECC_HIGH}
            For i As Integer = 0 To levels.Length - 1
                If dataUsedBits <= GetNumDataCodewords(version, levels(i)) * 8 Then
                    ecl = levels(i)
                End If
            Next
        End If

        ' Concatenate all segments to create the data bit string
        Dim bb As New BitBuffer()
        For Each seg As QrSegment In segs
            bb.AppendBits(seg.modeBits, 4)
            bb.AppendBits(seg.numChars, seg.NumCharCountBits(version))
            bb.AppendData(seg.data)
        Next

        ' Add terminator and pad up to a byte if applicable
        Dim dataCapacityBits2 As Integer = GetNumDataCodewords(version, ecl) * 8
        Dim term As Integer = 4
        If dataCapacityBits2 - bb.BitLength < 4 Then
            term = dataCapacityBits2 - bb.BitLength
        End If
        bb.AppendBits(0, term)
        bb.AppendBits(0, (8 - (bb.BitLength Mod 8)) Mod 8)

        ' Pad with alternating bytes until data capacity is reached
        Dim padByte As Integer = &HEC
        While bb.BitLength < dataCapacityBits2
            bb.AppendBits(padByte, 8)
            padByte = padByte Xor (&HEC Xor &H11)
        End While

        ' Pack bits into bytes in big endian
        Dim dataCodewords(dataCapacityBits2 \ 8 - 1) As Byte
        For i As Integer = 0 To bb.BitLength - 1
            If bb.GetBit(i) Then
                Dim idx As Integer = i >> 3
                dataCodewords(idx) = CByte(dataCodewords(idx) Or (1 << (7 - (i And 7))))
            End If
        Next

        ' Create the QR Code object
        Return New QrCode(version, ecl, dataCodewords, mask)
    End Function

    ' Instance fields
    Public version As Integer
    Public size As Integer
    Public errorCorrectionLevel As QrEcc
    Public mask As Integer
    Private modules(,) As Boolean
    Private isFunction(,) As Boolean

    Public Sub New(ByVal ver As Integer, ByVal ecl As QrEcc, ByVal dataCodewords As Byte(), ByVal msk As Integer)
        If ver < MIN_VERSION OrElse ver > MAX_VERSION Then
            Throw New ArgumentException("Version value out of range")
        End If
        If msk < -1 OrElse msk > 7 Then
            Throw New ArgumentException("Mask value out of range")
        End If
        version = ver
        size = ver * 4 + 17
        errorCorrectionLevel = ecl
        ReDim modules(size - 1, size - 1)
        ReDim isFunction(size - 1, size - 1)

        drawFunctionPatterns()
        Dim allCodewords As Byte() = addEccAndInterleave(dataCodewords)
        drawCodewords(allCodewords)

        If msk = -1 Then
            Dim minPenalty As Integer = Integer.MaxValue
            For i As Integer = 0 To 7
                applyMask(i)
                drawFormatBits(i)
                Dim penalty As Integer = getPenaltyScore()
                If penalty < minPenalty Then
                    msk = i
                    minPenalty = penalty
                End If
                applyMask(i)
            Next
        End If

        mask = msk
        applyMask(msk)
        drawFormatBits(msk)
        isFunction = Nothing
    End Sub

    Public Function GetModule(ByVal x As Integer, ByVal y As Integer) As Boolean
        Return x >= 0 AndAlso x < size AndAlso y >= 0 AndAlso y < size AndAlso modules(y, x)
    End Function

    ' Renders the QR code to a Bitmap. scale = pixels per module, border = quiet zone modules.
    Public Function RenderToBitmap(ByVal scale As Integer, ByVal border As Integer) As System.Drawing.Bitmap
        Dim dimen As Integer = (size + border * 2) * scale
        Dim bmp As New System.Drawing.Bitmap(dimen, dimen)
        Dim g As System.Drawing.Graphics = System.Drawing.Graphics.FromImage(bmp)
        Dim white As New System.Drawing.SolidBrush(System.Drawing.Color.White)
        Dim black As New System.Drawing.SolidBrush(System.Drawing.Color.Black)
        Try
            g.FillRectangle(white, 0, 0, dimen, dimen)
            For y As Integer = 0 To size - 1
                For x As Integer = 0 To size - 1
                    If modules(y, x) Then
                        g.FillRectangle(black, (x + border) * scale, (y + border) * scale, scale, scale)
                    End If
                Next
            Next
        Finally
            white.Dispose()
            black.Dispose()
            g.Dispose()
        End Try
        Return bmp
    End Function

    ' ---- Drawing function modules ----

    Private Sub drawFunctionPatterns()
        ' Timing patterns
        For i As Integer = 0 To size - 1
            setFunctionModule(6, i, (i Mod 2) = 0)
            setFunctionModule(i, 6, (i Mod 2) = 0)
        Next

        ' Finder patterns
        drawFinderPattern(3, 3)
        drawFinderPattern(size - 4, 3)
        drawFinderPattern(3, size - 4)

        ' Alignment patterns
        Dim alignPatPos As Integer() = getAlignmentPatternPositions()
        Dim numAlign As Integer = alignPatPos.Length
        For i As Integer = 0 To numAlign - 1
            For j As Integer = 0 To numAlign - 1
                If Not ((i = 0 AndAlso j = 0) OrElse (i = 0 AndAlso j = numAlign - 1) OrElse (i = numAlign - 1 AndAlso j = 0)) Then
                    drawAlignmentPattern(alignPatPos(i), alignPatPos(j))
                End If
            Next
        Next

        ' Configuration data
        drawFormatBits(0)
        drawVersion()
    End Sub

    Private Sub drawFormatBits(ByVal msk As Integer)
        ' Calculate error correction code and pack bits
        Dim data As Integer = (GetFormatBits(errorCorrectionLevel) << 3) Or msk
        Dim remd As Integer = data
        For i As Integer = 0 To 9
            remd = (remd << 1) Xor ((remd >> 9) * &H537)
        Next
        Dim bits As Integer = ((data << 10) Or remd) Xor &H5412

        ' Draw first copy
        For i As Integer = 0 To 5
            setFunctionModule(8, i, getBit(bits, i))
        Next
        setFunctionModule(8, 7, getBit(bits, 6))
        setFunctionModule(8, 8, getBit(bits, 7))
        setFunctionModule(7, 8, getBit(bits, 8))
        For i As Integer = 9 To 14
            setFunctionModule(14 - i, 8, getBit(bits, i))
        Next

        ' Draw second copy
        For i As Integer = 0 To 7
            setFunctionModule(size - 1 - i, 8, getBit(bits, i))
        Next
        For i As Integer = 8 To 14
            setFunctionModule(8, size - 15 + i, getBit(bits, i))
        Next
        setFunctionModule(8, size - 8, True)
    End Sub

    Private Sub drawVersion()
        If version < 7 Then
            Return
        End If

        ' Calculate error correction code and pack bits
        Dim remd As Integer = version
        For i As Integer = 0 To 11
            remd = (remd << 1) Xor ((remd >> 11) * &H1F25)
        Next
        Dim bits As Integer = (version << 12) Or remd

        ' Draw two copies
        For i As Integer = 0 To 17
            Dim bit As Boolean = getBit(bits, i)
            Dim a As Integer = size - 11 + (i Mod 3)
            Dim b As Integer = i \ 3
            setFunctionModule(a, b, bit)
            setFunctionModule(b, a, bit)
        Next
    End Sub

    Private Sub drawFinderPattern(ByVal x As Integer, ByVal y As Integer)
        For dy As Integer = -4 To 4
            For dx As Integer = -4 To 4
                Dim dist As Integer = System.Math.Max(System.Math.Abs(dx), System.Math.Abs(dy))
                Dim xx As Integer = x + dx
                Dim yy As Integer = y + dy
                If xx >= 0 AndAlso xx < size AndAlso yy >= 0 AndAlso yy < size Then
                    setFunctionModule(xx, yy, Not (dist = 2 OrElse dist = 4))
                End If
            Next
        Next
    End Sub

    Private Sub drawAlignmentPattern(ByVal x As Integer, ByVal y As Integer)
        For dy As Integer = -2 To 2
            For dx As Integer = -2 To 2
                setFunctionModule(x + dx, y + dy, System.Math.Max(System.Math.Abs(dx), System.Math.Abs(dy)) <> 1)
            Next
        Next
    End Sub

    Private Sub setFunctionModule(ByVal x As Integer, ByVal y As Integer, ByVal isDark As Boolean)
        modules(y, x) = isDark
        isFunction(y, x) = True
    End Sub

    ' ---- Codewords and masking ----

    Private Function addEccAndInterleave(ByVal data As Byte()) As Byte()
        If data.Length <> GetNumDataCodewords(version, errorCorrectionLevel) Then
            Throw New ArgumentException("Invalid data length")
        End If

        Dim numBlocks As Integer = NUM_ERROR_CORRECTION_BLOCKS(errorCorrectionLevel, version)
        Dim blockEccLen As Integer = ECC_CODEWORDS_PER_BLOCK(errorCorrectionLevel, version)
        Dim rawCodewords As Integer = getNumRawDataModules(version) \ 8
        Dim numShortBlocks As Integer = numBlocks - (rawCodewords Mod numBlocks)
        Dim shortBlockLen As Integer = rawCodewords \ numBlocks

        ' Split data into blocks and append ECC to each block
        Dim blocks(numBlocks - 1)() As Byte
        Dim rsDiv As Byte() = reedSolomonComputeDivisor(blockEccLen)
        Dim k As Integer = 0
        For i As Integer = 0 To numBlocks - 1
            Dim extra As Integer = 0
            If i >= numShortBlocks Then
                extra = 1
            End If
            Dim datLen As Integer = shortBlockLen - blockEccLen + extra
            Dim dat(datLen - 1) As Byte
            Array.Copy(data, k, dat, 0, datLen)
            k += datLen
            Dim block(shortBlockLen) As Byte
            Array.Copy(dat, 0, block, 0, datLen)
            Dim ecc As Byte() = reedSolomonComputeRemainder(dat, rsDiv)
            Array.Copy(ecc, 0, block, block.Length - blockEccLen, ecc.Length)
            blocks(i) = block
        Next

        ' Interleave (not concatenate) the bytes from every block
        Dim result(rawCodewords - 1) As Byte
        k = 0
        For i As Integer = 0 To blocks(0).Length - 1
            For j As Integer = 0 To blocks.Length - 1
                If Not (i = shortBlockLen - blockEccLen AndAlso j < numShortBlocks) Then
                    result(k) = blocks(j)(i)
                    k += 1
                End If
            Next
        Next
        Return result
    End Function

    Private Sub drawCodewords(ByVal data As Byte())
        If data.Length <> getNumRawDataModules(version) \ 8 Then
            Throw New ArgumentException("Invalid data length")
        End If

        Dim i As Integer = 0
        Dim right As Integer = size - 1
        While right >= 1
            If right = 6 Then
                right = 5
            End If
            For vert As Integer = 0 To size - 1
                For j As Integer = 0 To 1
                    Dim x As Integer = right - j
                    Dim upward As Boolean = ((right + 1) And 2) = 0
                    Dim y As Integer = vert
                    If upward Then
                        y = size - 1 - vert
                    End If
                    If (Not isFunction(y, x)) AndAlso i < data.Length * 8 Then
                        modules(y, x) = getBit(data(i >> 3), 7 - (i And 7))
                        i += 1
                    End If
                Next
            Next
            right -= 2
        End While
    End Sub

    Private Sub applyMask(ByVal msk As Integer)
        If msk < 0 OrElse msk > 7 Then
            Throw New ArgumentException("Mask value out of range")
        End If
        For y As Integer = 0 To size - 1
            For x As Integer = 0 To size - 1
                Dim invert As Boolean
                Select Case msk
                    Case 0
                        invert = ((x + y) Mod 2) = 0
                    Case 1
                        invert = (y Mod 2) = 0
                    Case 2
                        invert = (x Mod 3) = 0
                    Case 3
                        invert = ((x + y) Mod 3) = 0
                    Case 4
                        invert = (((x \ 3) + (y \ 2)) Mod 2) = 0
                    Case 5
                        invert = ((x * y Mod 2) + (x * y Mod 3)) = 0
                    Case 6
                        invert = (((x * y Mod 2) + (x * y Mod 3)) Mod 2) = 0
                    Case Else
                        invert = ((((x + y) Mod 2) + (x * y Mod 3)) Mod 2) = 0
                End Select
                If invert AndAlso (Not isFunction(y, x)) Then
                    modules(y, x) = Not modules(y, x)
                End If
            Next
        Next
    End Sub

    Private Function getPenaltyScore() As Integer
        Dim result As Integer = 0

        ' Adjacent modules in row having same color, and finder-like patterns
        For y As Integer = 0 To size - 1
            Dim runColor As Boolean = False
            Dim runX As Integer = 0
            Dim runHistory(6) As Integer
            For x As Integer = 0 To size - 1
                If modules(y, x) = runColor Then
                    runX += 1
                    If runX = 5 Then
                        result += PENALTY_N1
                    ElseIf runX > 5 Then
                        result += 1
                    End If
                Else
                    finderPenaltyAddHistory(runX, runHistory)
                    If Not runColor Then
                        result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3
                    End If
                    runColor = modules(y, x)
                    runX = 1
                End If
            Next
            result += finderPenaltyTerminateAndCount(runColor, runX, runHistory) * PENALTY_N3
        Next

        ' Adjacent modules in column having same color, and finder-like patterns
        For x As Integer = 0 To size - 1
            Dim runColor As Boolean = False
            Dim runY As Integer = 0
            Dim runHistory(6) As Integer
            For y As Integer = 0 To size - 1
                If modules(y, x) = runColor Then
                    runY += 1
                    If runY = 5 Then
                        result += PENALTY_N1
                    ElseIf runY > 5 Then
                        result += 1
                    End If
                Else
                    finderPenaltyAddHistory(runY, runHistory)
                    If Not runColor Then
                        result += finderPenaltyCountPatterns(runHistory) * PENALTY_N3
                    End If
                    runColor = modules(y, x)
                    runY = 1
                End If
            Next
            result += finderPenaltyTerminateAndCount(runColor, runY, runHistory) * PENALTY_N3
        Next

        ' 2*2 blocks of modules having same color
        For y As Integer = 0 To size - 2
            For x As Integer = 0 To size - 2
                Dim color As Boolean = modules(y, x)
                If color = modules(y, x + 1) AndAlso color = modules(y + 1, x) AndAlso color = modules(y + 1, x + 1) Then
                    result += PENALTY_N2
                End If
            Next
        Next

        ' Balance of dark and light modules
        Dim dark As Integer = 0
        For y As Integer = 0 To size - 1
            For x As Integer = 0 To size - 1
                If modules(y, x) Then
                    dark += 1
                End If
            Next
        Next
        Dim total As Integer = size * size
        Dim k As Integer = (System.Math.Abs(dark * 20 - total * 10) + total - 1) \ total - 1
        result += k * PENALTY_N4

        Return result
    End Function

    ' ---- Private helper functions ----

    Private Function getAlignmentPatternPositions() As Integer()
        If version = 1 Then
            Return New Integer() {}
        Else
            Dim numAlign As Integer = version \ 7 + 2
            Dim stepSize As Integer = ((version * 8 + numAlign * 3 + 5) \ (numAlign * 4 - 4)) * 2
            Dim result(numAlign - 1) As Integer
            result(0) = 6
            Dim pos As Integer = size - 7
            For i As Integer = numAlign - 1 To 1 Step -1
                result(i) = pos
                pos -= stepSize
            Next
            Return result
        End If
    End Function

    Private Shared Function getNumRawDataModules(ByVal ver As Integer) As Integer
        If ver < MIN_VERSION OrElse ver > MAX_VERSION Then
            Throw New ArgumentException("Version number out of range")
        End If
        Dim sz As Integer = ver * 4 + 17
        Dim result As Integer = sz * sz
        result -= 8 * 8 * 3
        result -= 15 * 2 + 1
        result -= (sz - 16) * 2
        If ver >= 2 Then
            Dim numAlign As Integer = ver \ 7 + 2
            result -= (numAlign - 1) * (numAlign - 1) * 25
            result -= (numAlign - 2) * 2 * 20
            If ver >= 7 Then
                result -= 6 * 3 * 2
            End If
        End If
        Return result
    End Function

    Private Shared Function reedSolomonComputeDivisor(ByVal degree As Integer) As Byte()
        If degree < 1 OrElse degree > 255 Then
            Throw New ArgumentException("Degree out of range")
        End If
        Dim result(degree - 1) As Byte
        result(degree - 1) = 1
        Dim root As Integer = 1
        For i As Integer = 0 To degree - 1
            For j As Integer = 0 To result.Length - 1
                result(j) = CByte(reedSolomonMultiply(result(j) And &HFF, root))
                If j + 1 < result.Length Then
                    result(j) = CByte(result(j) Xor result(j + 1))
                End If
            Next
            root = reedSolomonMultiply(root, &H2)
        Next
        Return result
    End Function

    Private Shared Function reedSolomonComputeRemainder(ByVal data As Byte(), ByVal divisor As Byte()) As Byte()
        Dim result(divisor.Length - 1) As Byte
        For Each b As Byte In data
            Dim factor As Integer = (b Xor result(0)) And &HFF
            Array.Copy(result, 1, result, 0, result.Length - 1)
            result(result.Length - 1) = 0
            For i As Integer = 0 To result.Length - 1
                result(i) = CByte(result(i) Xor CByte(reedSolomonMultiply(divisor(i) And &HFF, factor)))
            Next
        Next
        Return result
    End Function

    Private Shared Function reedSolomonMultiply(ByVal x As Integer, ByVal y As Integer) As Integer
        Dim z As Integer = 0
        For i As Integer = 7 To 0 Step -1
            z = (z << 1) Xor ((z >> 7) * &H11D)
            z = z Xor (((y >> i) And 1) * x)
        Next
        Return z
    End Function

    Private Shared Function GetNumDataCodewords(ByVal ver As Integer, ByVal ecl As QrEcc) As Integer
        Return getNumRawDataModules(ver) \ 8 - ECC_CODEWORDS_PER_BLOCK(ecl, ver) * NUM_ERROR_CORRECTION_BLOCKS(ecl, ver)
    End Function

    Private Function finderPenaltyCountPatterns(ByVal runHistory As Integer()) As Integer
        Dim n As Integer = runHistory(1)
        Dim core As Boolean = n > 0 AndAlso runHistory(2) = n AndAlso runHistory(3) = n * 3 AndAlso runHistory(4) = n AndAlso runHistory(5) = n
        Dim total As Integer = 0
        If core AndAlso runHistory(0) >= n * 4 AndAlso runHistory(6) >= n Then
            total += 1
        End If
        If core AndAlso runHistory(6) >= n * 4 AndAlso runHistory(0) >= n Then
            total += 1
        End If
        Return total
    End Function

    Private Function finderPenaltyTerminateAndCount(ByVal currentRunColor As Boolean, ByVal currentRunLength As Integer, ByVal runHistory As Integer()) As Integer
        If currentRunColor Then
            finderPenaltyAddHistory(currentRunLength, runHistory)
            currentRunLength = 0
        End If
        currentRunLength += size
        finderPenaltyAddHistory(currentRunLength, runHistory)
        Return finderPenaltyCountPatterns(runHistory)
    End Function

    Private Sub finderPenaltyAddHistory(ByVal currentRunLength As Integer, ByVal runHistory As Integer())
        If runHistory(0) = 0 Then
            currentRunLength += size
        End If
        Array.Copy(runHistory, 0, runHistory, 1, runHistory.Length - 1)
        runHistory(0) = currentRunLength
    End Sub

    Private Shared Function getBit(ByVal x As Integer, ByVal i As Integer) As Boolean
        Return ((x >> i) And 1) <> 0
    End Function

    Private Const PENALTY_N1 As Integer = 3
    Private Const PENALTY_N2 As Integer = 3
    Private Const PENALTY_N3 As Integer = 40
    Private Const PENALTY_N4 As Integer = 10

    ' [ecl, version 0..40]; index 0 is padding and unused.
    Private Shared ReadOnly ECC_CODEWORDS_PER_BLOCK As Integer(,)
    Private Shared ReadOnly NUM_ERROR_CORRECTION_BLOCKS As Integer(,)

    Shared Sub New()
        ReDim ECC_CODEWORDS_PER_BLOCK(3, 40)
        Dim ecc As String() = New String() {"-1 7 10 15 20 26 18 20 24 30 18 20 24 26 30 22 24 28 30 28 28 28 28 30 30 26 28 30 30 30 30 30 30 30 30 30 30 30 30 30 30", "-1 10 16 26 18 24 16 18 22 22 26 30 22 22 24 24 28 28 26 26 26 26 28 28 28 28 28 28 28 28 28 28 28 28 28 28 28 28 28 28 28", "-1 13 22 18 26 18 24 18 22 20 24 28 26 24 20 30 24 28 28 26 30 28 30 30 30 30 28 30 30 30 30 30 30 30 30 30 30 30 30 30 30", "-1 17 28 22 16 22 28 26 26 24 28 24 28 22 24 24 30 28 28 26 28 30 24 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30 30"}
        For r As Integer = 0 To 3
            Dim parts As String() = ecc(r).Split(" "c)
            For c As Integer = 0 To 40
                ECC_CODEWORDS_PER_BLOCK(r, c) = Integer.Parse(parts(c))
            Next
        Next

        ReDim NUM_ERROR_CORRECTION_BLOCKS(3, 40)
        Dim nblk As String() = New String() {"-1 1 1 1 1 1 2 2 2 2 4 4 4 4 4 6 6 6 6 7 8 8 9 9 10 12 12 12 13 14 15 16 17 18 19 19 20 21 22 24 25", "-1 1 1 1 2 2 4 4 4 5 5 5 8 9 9 10 10 11 13 14 16 17 17 18 20 21 23 25 26 28 29 31 33 35 37 38 40 43 45 47 49", "-1 1 1 2 2 4 4 6 6 8 8 8 10 12 16 12 17 16 18 21 20 23 23 25 27 29 34 34 35 38 40 43 45 48 51 53 56 59 62 65 68", "-1 1 1 2 4 4 4 5 6 8 8 11 11 16 16 18 16 19 21 25 25 25 34 30 32 35 37 40 42 45 48 51 54 57 60 63 66 70 74 77 81"}
        For r As Integer = 0 To 3
            Dim parts As String() = nblk(r).Split(" "c)
            For c As Integer = 0 To 40
                NUM_ERROR_CORRECTION_BLOCKS(r, c) = Integer.Parse(parts(c))
            Next
        Next
    End Sub

End Class

' ---- Bit buffer for building the data bit stream ----

Public Class BitBuffer

    Private mData As Byte()
    Private mBitLength As Integer

    Public Sub New()
        ReDim mData(7)
        mBitLength = 0
    End Sub

    Public ReadOnly Property BitLength() As Integer
        Get
            Return mBitLength
        End Get
    End Property

    Public Function GetBit(ByVal index As Integer) As Boolean
        If index < 0 OrElse index >= mBitLength Then
            Throw New ArgumentOutOfRangeException("Index")
        End If
        Return ((mData(index >> 3) >> (index And 7)) And 1) <> 0
    End Function

    Public Sub AppendBits(ByVal val As Integer, ByVal len As Integer)
        If len < 0 OrElse len > 31 OrElse (val >> len) <> 0 Then
            Throw New ArgumentOutOfRangeException("Length or value")
        End If
        For i As Integer = len - 1 To 0 Step -1
            If mBitLength = mData.Length * 8 Then
                Dim n(mData.Length * 2 - 1) As Byte
                Array.Copy(mData, 0, n, 0, mData.Length)
                mData = n
            End If
            If ((val >> i) And 1) <> 0 Then
                mData(mBitLength >> 3) = CByte(mData(mBitLength >> 3) Or (1 << (mBitLength And 7)))
            End If
            mBitLength += 1
        Next
    End Sub

    Public Sub AppendData(ByVal bb As BitBuffer)
        For i As Integer = 0 To bb.BitLength - 1
            If bb.GetBit(i) Then
                AppendBits(1, 1)
            Else
                AppendBits(0, 1)
            End If
        Next
    End Sub

End Class

' ---- A segment of data (byte mode suffices for the Bilibili login URL) ----

Public Class QrSegment

    Public Const MODE_BYTE As Integer = &H4

    Public modeBits As Integer
    Public numChars As Integer
    Public data As BitBuffer

    Public Sub New(ByVal md As Integer, ByVal numCh As Integer, ByVal dat As BitBuffer)
        modeBits = md
        numChars = numCh
        data = dat
    End Sub

    Public Function NumCharCountBits(ByVal ver As Integer) As Integer
        ' Byte mode char count bits by version range: [1-9]=8, [10-26]=16, [27-40]=16
        If ver <= 9 Then
            Return 8
        Else
            Return 16
        End If
    End Function

    Public Shared Function GetTotalBits(ByVal segs As System.Collections.ArrayList, ByVal version As Integer) As Integer
        Dim result As Long = 0
        For Each seg As QrSegment In segs
            Dim ccbits As Integer = seg.NumCharCountBits(version)
            If seg.numChars >= (1 << ccbits) Then
                Return -1
            End If
            result += 4 + ccbits + seg.data.BitLength
            If result > Integer.MaxValue Then
                Return -1
            End If
        Next
        Return CInt(result)
    End Function

End Class
