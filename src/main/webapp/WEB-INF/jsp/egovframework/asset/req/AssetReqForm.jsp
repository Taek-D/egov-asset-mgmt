<%--
  신청서 작성 (모든 사용자). 제출하면 바로 '대기'. 화면마다 받는 1회용 제출 토큰(assetReqToken)으로 중복 제출을 막는다.
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>신청서 작성</title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
<script type="text/javascript">
// 가용보다 많이 적으면 경고만 한다(제출은 막지 않는다. 재고 확인은 승인 때)
function fn_checkAvail(i) {
	var sel = document.getElementById('itemCode' + i);
	var qty = document.getElementById('reqQty' + i);
	var warn = document.getElementById('warn' + i);
	var opt = sel.options[sel.selectedIndex];
	var avail = opt ? parseInt(opt.getAttribute('data-avail'), 10) : NaN;
	var n = parseInt(qty.value, 10);
	warn.textContent = (!isNaN(avail) && !isNaN(n) && n > avail) ? '가용(' + avail + ')보다 많습니다. 제출은 되지만 승인 때 재고가 모자라면 승인되지 않을 수 있습니다.' : '';
}
</script>
</head>
<body>

<div class="wTableFrm">
	<h2>신청서 작성</h2>

	<c:if test="${not empty message}">
	<p role="alert" style="margin:0 0 7px; padding:8px 10px; border:1px solid #b1bbcb; background:#fff8e5;"><c:out value="${message}"/></p>
	</c:if>

	<p>작성하면 바로 '대기' 상태가 됩니다(임시저장 없음). 대기 중에는 고칠 수 없으니 바꾸려면 취소 후 다시 신청하세요.
	같은 품목을 두 줄에 적으면 한 줄로 합쳐집니다(수량 합산). 가용보다 많이 적어도 제출할 수 있으며 재고는 승인 때 확인합니다.</p>

	<form name="reqForm" action="<c:url value='/asset/req/submit.do'/>" method="post">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<input type="hidden" name="assetReqToken" value="<c:out value='${assetReqToken}'/>"/>

	<table class="wTable">
	<caption>신청 사유</caption>
	<colgroup><col style="width:20%;"><col></colgroup>
	<tbody>
	<tr>
		<th scope="row"><label for="reqResn">신청 사유</label></th>
		<td class="left"><textarea id="reqResn" name="reqResn" title="신청 사유 입력" rows="3" cols="70" maxlength="1000"><c:out value="${req.reqResn}"/></textarea></td>
	</tr>
	</tbody>
	</table>

	<table class="wTable" style="margin-top:10px;">
	<caption>신청 품목(1개 이상)</caption>
	<colgroup><col style="width:20%;"><col></colgroup>
	<tbody>
	<c:forEach items="${req.lines}" var="ln" varStatus="st">
	<tr>
		<th scope="row"><label for="itemCode${st.index}">품목 ${st.count}<c:if test="${st.first}"> <span class="pilsu">*</span></c:if></label></th>
		<td class="left">
			<select id="itemCode${st.index}" name="lines[${st.index}].itemCode" title="품목 ${st.count} 선택" onchange="fn_checkAvail(${st.index})">
				<option value="">선택</option>
				<c:forEach items="${itemList}" var="it">
				<option value="<c:out value='${it.itemCode}'/>" data-avail="${it.availQty}" <c:if test="${ln.itemCode == it.itemCode}">selected="selected"</c:if>><c:out value="${it.itemNm}"/> (가용 <c:out value="${it.availQty}"/> <c:out value="${it.unitCodeNm}"/>)</option>
				</c:forEach>
			</select>
			<label for="reqQty${st.index}">수량</label>
			<input type="number" id="reqQty${st.index}" name="lines[${st.index}].reqQty" title="품목 ${st.count} 신청 수량(1 이상)" min="1" size="6" value="<c:out value='${ln.reqQty}'/>" oninput="fn_checkAvail(${st.index})"/>
			<span id="warn${st.index}" role="status" aria-live="polite" style="color:#c00;"></span>
		</td>
	</tr>
	</c:forEach>
	</tbody>
	</table>
	<c:if test="${fn:length(itemList) == 0}"><p>사용 중인 품목이 없습니다.</p></c:if>

	<div class="btn">
		<input type="submit" class="s_submit" value="제출" title="신청서 제출(바로 대기 상태)"/>
		<button type="submit" class="s_submit" name="addRows" value="Y" formaction="<c:url value='/asset/req/form.do'/>" formnovalidate="formnovalidate" title="입력한 내용을 두고 품목 줄 5개를 더합니다">품목 줄 추가</button>
		<span class="btn_s"><a href="<c:url value='/asset/req/myList.do'/>" title="내 신청 목록으로 이동">내 신청</a></span>
	</div>
	</form>

	<h2 style="clear:both; margin-top:20px;">품목별 가용 수량</h2>
	<table class="board_list">
	<caption>사용 중인 품목의 현재 가용 수량</caption>
	<colgroup><col><col style="width:15%;"><col style="width:15%;"></colgroup>
	<thead>
	<tr><th scope="col">품목</th><th scope="col">유형</th><th scope="col">가용 수량</th></tr>
	</thead>
	<tbody>
	<c:forEach items="${itemList}" var="it">
	<tr>
		<td class="left"><c:out value="${it.itemNm}"/></td>
		<td><c:choose><c:when test="${it.itemTy == 'UNIT'}">개별 자산</c:when><c:otherwise>수량 비품</c:otherwise></c:choose></td>
		<td><c:out value="${it.availQty}"/> <c:out value="${it.unitCodeNm}"/></td>
	</tr>
	</c:forEach>
	</tbody>
	</table>
</div>

<script type="text/javascript">
for (var i = 0; document.getElementById('itemCode' + i); i++) { fn_checkAvail(i); }
</script>
</body>
</html>
