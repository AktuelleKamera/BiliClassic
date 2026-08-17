' HmacUtil.vb
' Pure-managed SHA-1 + HMAC-SHA1 for .NET Compact Framework 2.0.
' CF mscorlib has no System.Security.Cryptography.SHA1/HMACSHA1, so we
' implement the standard FIPS 180-1 / RFC 2104 algorithms ourselves.

Public Class HmacUtil

    ' ---- SHA-1 (FIPS 180-1) ----

    Public Shared Function Sha1Hex(ByVal message As String) As String
        Dim data As Byte() = System.Text.Encoding.UTF8.GetBytes(message)
        Dim hash As Byte() = Sha1Bytes(data)
        Dim sb As New System.Text.StringBuilder(hash.Length * 2)
        For i As Integer = 0 To hash.Length - 1
            sb.Append(hash(i).ToString("x2"))
        Next
        Return sb.ToString()
    End Function

    Private Shared Function Sha1Bytes(ByVal data As Byte()) As Byte()
        ' Pre-processing: append 0x80, pad to 56 mod 64, append 64-bit bit length.
        Dim origLen As Long = data.Length
        Dim ml As Long = origLen * 8
        Dim newLen As Integer = (((data.Length + 8) \ 64) + 1) * 64
        Dim msg(newLen - 1) As Byte
        System.Array.Copy(data, msg, data.Length)
        msg(data.Length) = &H80
        ' Write bit length (big-endian, top 32 bits then low 32 bits) at end
        Dim hi As Long = ml >> 32
        Dim lo As Long = ml And &HFFFFFFFFL
        msg(newLen - 8) = CByte((hi >> 24) And &HFF)
        msg(newLen - 7) = CByte((hi >> 16) And &HFF)
        msg(newLen - 6) = CByte((hi >> 8) And &HFF)
        msg(newLen - 5) = CByte(hi And &HFF)
        msg(newLen - 4) = CByte((lo >> 24) And &HFF)
        msg(newLen - 3) = CByte((lo >> 16) And &HFF)
        msg(newLen - 2) = CByte((lo >> 8) And &HFF)
        msg(newLen - 1) = CByte(lo And &HFF)

        Dim h0 As UInteger = &H67452301UI
        Dim h1 As UInteger = &HEFCDAB89UI
        Dim h2 As UInteger = &H98BADCFEUI
        Dim h3 As UInteger = &H10325476UI
        Dim h4 As UInteger = &HC3D2E1F0UI

        Dim w(79) As UInteger
        Dim nBlocks As Integer = newLen \ 64
        For b As Integer = 0 To nBlocks - 1
            Dim base As Integer = b * 64
            For i As Integer = 0 To 15
                w(i) = (CUInt(msg(base + i * 4)) << 24) Or _
                       (CUInt(msg(base + i * 4 + 1)) << 16) Or _
                       (CUInt(msg(base + i * 4 + 2)) << 8) Or _
                       CUInt(msg(base + i * 4 + 3))
            Next
            For i As Integer = 16 To 79
                w(i) = Rotl((w(i - 3) Xor w(i - 8) Xor w(i - 14) Xor w(i - 16)), 1)
            Next

            Dim a As UInteger = h0
            Dim bb As UInteger = h1
            Dim c As UInteger = h2
            Dim d As UInteger = h3
            Dim e As UInteger = h4

            For i As Integer = 0 To 79
                Dim f As UInteger
                Dim k As UInteger
                If i < 20 Then
                    f = (bb And c) Or ((Not bb) And d)
                    k = &H5A827999UI
                ElseIf i < 40 Then
                    f = bb Xor c Xor d
                    k = &H6ED9EBA1UI
                ElseIf i < 60 Then
                    f = (bb And c) Or (bb And d) Or (c And d)
                    k = &H8F1BBCDCUI
                Else
                    f = bb Xor c Xor d
                    k = &HCA62C1D6UI
                End If
                Dim acc As Long = CLng(Rotl(a, 5)) + CLng(f) + CLng(e) + CLng(k) + CLng(w(i))
                Dim temp As UInteger = CUInt(acc And &HFFFFFFFFL)
                e = d
                d = c
                c = Rotl(bb, 30)
                bb = a
                a = temp
            Next

            h0 = CUInt((CLng(h0) + CLng(a)) And &HFFFFFFFFL)
            h1 = CUInt((CLng(h1) + CLng(bb)) And &HFFFFFFFFL)
            h2 = CUInt((CLng(h2) + CLng(c)) And &HFFFFFFFFL)
            h3 = CUInt((CLng(h3) + CLng(d)) And &HFFFFFFFFL)
            h4 = CUInt((CLng(h4) + CLng(e)) And &HFFFFFFFFL)
        Next

        Dim out(19) As Byte
        PutUInt32(out, 0, h0)
        PutUInt32(out, 4, h1)
        PutUInt32(out, 8, h2)
        PutUInt32(out, 12, h3)
        PutUInt32(out, 16, h4)
        Return out
    End Function

    Private Shared Function Rotl(ByVal x As UInteger, ByVal n As Integer) As UInteger
        Return (x << n) Or (x >> (32 - n))
    End Function

    Private Shared Sub PutUInt32(ByVal buf As Byte(), ByVal offset As Integer, ByVal v As UInteger)
        buf(offset) = CByte((v >> 24) And &HFF)
        buf(offset + 1) = CByte((v >> 16) And &HFF)
        buf(offset + 2) = CByte((v >> 8) And &HFF)
        buf(offset + 3) = CByte(v And &HFF)
    End Sub

    ' ---- HMAC-SHA1 (RFC 2104) ----

    Public Shared Function HmacSha1Hex(ByVal key As String, ByVal message As String) As String
        Try
            Dim keyBytes As Byte() = System.Text.Encoding.UTF8.GetBytes(key)
            Dim msgBytes As Byte() = System.Text.Encoding.UTF8.GetBytes(message)
            Dim blockSize As Integer = 64
            If keyBytes.Length > blockSize Then
                keyBytes = Sha1Bytes(keyBytes)
            End If
            Dim ipad(blockSize - 1) As Byte
            Dim opad(blockSize - 1) As Byte
            For i As Integer = 0 To blockSize - 1
                Dim kb As Byte = 0
                If i < keyBytes.Length Then
                    kb = keyBytes(i)
                End If
                ipad(i) = kb Xor &H36
                opad(i) = kb Xor &H5C
            Next

            ' inner: SHA1(ipad || message)
            Dim innerLen As Integer = blockSize + msgBytes.Length
            Dim inner(innerLen - 1) As Byte
            System.Array.Copy(ipad, inner, blockSize)
            System.Array.Copy(msgBytes, 0, inner, blockSize, msgBytes.Length)
            Dim innerHash As Byte() = Sha1Bytes(inner)

            ' outer: SHA1(opad || innerHash)
            Dim outerLen As Integer = blockSize + innerHash.Length
            Dim outer(outerLen - 1) As Byte
            System.Array.Copy(opad, outer, blockSize)
            System.Array.Copy(innerHash, 0, outer, blockSize, innerHash.Length)
            Dim outerHash As Byte() = Sha1Bytes(outer)

            Dim sb As New System.Text.StringBuilder(outerHash.Length * 2)
            For i As Integer = 0 To outerHash.Length - 1
                sb.Append(outerHash(i).ToString("x2"))
            Next
            Return sb.ToString()
        Catch ex As Exception
            Return Nothing
        End Try
    End Function

    ' ---- Pseudo-random hex (no RNG on CF) ----

    Public Shared Function RandomHex(ByVal byteCount As Integer) As String
        Try
            Dim sb As New System.Text.StringBuilder(byteCount * 2)
            Dim seed As Long = DateTime.UtcNow.Ticks Xor (System.Environment.TickCount * 7919L)
            Dim state As Long = seed And &H7FFFFFFFL
            For i As Integer = 0 To byteCount - 1
                ' Simple LCG kept inside 31 bits to avoid Long overflow
                state = (state * 48271L) And &H7FFFFFFFL
                Dim v As Integer = CInt((state >> 16) And &HFFL)
                sb.Append(v.ToString("x2"))
            Next
            Return sb.ToString()
        Catch ex As Exception
            Return DateTime.UtcNow.Ticks.ToString("x")
        End Try
    End Function

End Class