<%--
  품목 등록·상세(수정, 사용 중지/재사용, 삭제, 입고·조정) (자산담당자)
--%>
<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<c:set var="isNew" value="${empty item.itemCode}"/>
<c:set var="pageTitle"><c:choose><c:when test="${isNew}">품목 등록</c:when><c:otherwise>품목 상세</c:otherwise></c:choose></c:set>
<!DOCTYPE html>
<html lang="ko">
<head>
<meta http-equiv="content-type" content="text/html; charset=utf-8">
<title>${pageTitle}</title>
<link type="text/css" rel="stylesheet" href="<c:url value='/css/egovframework/com/com.css' />">
</head>
<body>

<div class="wTableFrm">
	<h2>${pageTitle}</h2>

	<c:if test="${not empty message}">
	<p role="alert" style="margin:0 0 7px; padding:8px 10px; border:1px solid #b1bbcb; background:#fff8e5;"><c:out value="${message}"/></p>
	</c:if>

	<form name="itemForm" action="<c:url value='/asset/item/save.do'/>" method="post">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<input type="hidden" name="itemCode" value="<c:out value='${item.itemCode}'/>"/>
	<table class="wTable">
	<caption>${pageTitle}</caption>
	<colgroup><col style="width:20%;"><col></colgroup>
	<tbody>
	<c:if test="${not isNew}">
	<tr>
		<th scope="row">품목코드</th>
		<td class="left"><c:out value="${item.itemCode}"/></td>
	</tr>
	</c:if>
	<tr>
		<th scope="row"><label for="itemNm">품목명 <span class="pilsu">*</span></label></th>
		<td class="left"><input type="text" id="itemNm" name="itemNm" title="품목명 입력" size="50" maxlength="100" required="required" value="<c:out value='${item.itemNm}'/>"/></td>
	</tr>
	<tr>
		<th scope="row"><label for="itemTy">유형 <span class="pilsu">*</span></label></th>
		<td class="left">
			<c:choose>
			<c:when test="${item.hasHist}">
				<%-- 이력이 생긴 품목은 유형을 바꿀 수 없다 --%>
				<input type="hidden" name="itemTy" value="<c:out value='${item.itemTy}'/>"/>
				<select id="itemTy" title="품목 유형(이력이 있어 변경 불가)" disabled="disabled">
					<option><c:choose><c:when test="${item.itemTy == 'UNIT'}">개별 자산</c:when><c:otherwise>수량 비품</c:otherwise></c:choose></option>
				</select>
				<span>재고 변동·신청 이력이 있어 유형을 바꿀 수 없습니다.</span>
			</c:when>
			<c:otherwise>
				<select id="itemTy" name="itemTy" title="품목 유형 선택">
					<option value="QTY" <c:if test="${item.itemTy == 'QTY'}">selected="selected"</c:if>>수량 비품(기관 전체 수량)</option>
					<option value="UNIT" <c:if test="${item.itemTy == 'UNIT'}">selected="selected"</c:if>>개별 자산(자산번호 관리)</option>
				</select>
			</c:otherwise>
			</c:choose>
		</td>
	</tr>
	<tr>
		<th scope="row"><label for="clCode">분류</label></th>
		<td class="left">
			<select id="clCode" name="clCode" title="분류 선택">
				<option value="">선택</option>
				<c:forEach items="${clCodeList}" var="code">
				<option value="<c:out value='${code.code}'/>" <c:if test="${item.clCode == code.code}">selected="selected"</c:if>><c:out value="${code.codeNm}"/></option>
				</c:forEach>
			</select>
		</td>
	</tr>
	<tr>
		<th scope="row"><label for="unitCode">단위</label></th>
		<td class="left">
			<select id="unitCode" name="unitCode" title="단위 선택">
				<option value="">선택</option>
				<c:forEach items="${unitCodeList}" var="code">
				<option value="<c:out value='${code.code}'/>" <c:if test="${item.unitCode == code.code}">selected="selected"</c:if>><c:out value="${code.codeNm}"/></option>
				</c:forEach>
			</select>
		</td>
	</tr>
	<tr>
		<th scope="row"><label for="minQty">최소 수량</label></th>
		<td class="left"><input type="number" id="minQty" name="minQty" title="최소 수량 입력(0 이상)" min="0" step="1" value="<c:out value='${item.minQty}'/>"/></td>
	</tr>
	<c:if test="${not isNew}">
	<tr>
		<th scope="row">사용 여부</th>
		<td class="left"><c:choose><c:when test="${item.useAt == 'Y'}">사용</c:when><c:otherwise>사용 중지(새 신청·일괄등록 제외)</c:otherwise></c:choose></td>
	</tr>
	<tr>
		<th scope="row">수량</th>
		<td class="left">
			<c:choose>
			<c:when test="${item.itemTy == 'UNIT'}">보관 <c:out value="${item.storedQty}"/> / 확보 <c:out value="${item.reservedQty}"/> / 가용 <strong><c:out value="${item.availQty}"/></strong></c:when>
			<c:otherwise>재고 <c:out value="${item.stockQty}"/> / 가용 <strong><c:out value="${item.availQty}"/></strong></c:otherwise>
			</c:choose>
			<a href="<c:url value='/asset/stock/history.do'><c:param name='itemCode' value='${item.itemCode}'/></c:url>">재고 이력 보기</a>
		</td>
	</tr>
	</c:if>
	</tbody>
	</table>
	<div class="btn">
		<input type="submit" class="s_submit" value="저장" title="품목 저장 버튼"/>
		<span class="btn_s"><a href="<c:url value='/asset/item/list.do'/>" title="목록으로 이동">목록</a></span>
	</div>
	</form>

	<c:if test="${not isNew}">
	<div class="btn" style="clear:both;">
		<c:choose>
		<c:when test="${item.useAt == 'Y'}">
		<form action="<c:url value='/asset/item/deactivate.do'/>" method="post" style="display:inline;" onsubmit="return confirm('사용 중지하시겠습니까? 새 신청과 일괄등록에서 빠집니다.');">
			<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
			<input type="hidden" name="itemCode" value="<c:out value='${item.itemCode}'/>"/>
			<input type="submit" class="s_submit" value="사용 중지" title="사용 중지 버튼"/>
		</form>
		</c:when>
		<c:otherwise>
		<form action="<c:url value='/asset/item/activate.do'/>" method="post" style="display:inline;">
			<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
			<input type="hidden" name="itemCode" value="<c:out value='${item.itemCode}'/>"/>
			<input type="submit" class="s_submit" value="다시 사용" title="다시 사용 버튼"/>
		</form>
		</c:otherwise>
		</c:choose>
		<form action="<c:url value='/asset/item/delete.do'/>" method="post" style="display:inline;" onsubmit="return confirm('삭제하시겠습니까?');">
			<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
			<input type="hidden" name="itemCode" value="<c:out value='${item.itemCode}'/>"/>
			<c:choose>
			<c:when test="${item.hasHist}">
			<input type="submit" class="s_submit" value="삭제" title="삭제 불가(이력 있음)" disabled="disabled"/>
			<span>재고 변동·신청 이력이 있어 삭제할 수 없습니다. 사용 중지를 이용하세요.</span>
			</c:when>
			<c:otherwise>
			<input type="submit" class="s_submit" value="삭제" title="품목 삭제 버튼"/>
			</c:otherwise>
			</c:choose>
		</form>
	</div>

	<c:if test="${item.itemTy == 'QTY'}">
	<h2 style="clear:both; margin-top:20px;">입고</h2>
	<form action="<c:url value='/asset/item/receive.do'/>" method="post">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<input type="hidden" name="itemCode" value="<c:out value='${item.itemCode}'/>"/>
	<table class="wTable">
	<caption>입고</caption>
	<colgroup><col style="width:20%;"><col></colgroup>
	<tbody>
	<tr>
		<th scope="row"><label for="receiveQty">입고 수량 <span class="pilsu">*</span></label></th>
		<td class="left"><input type="number" id="receiveQty" name="qty" title="입고 수량 입력(1 이상)" min="1" step="1" required="required"/></td>
	</tr>
	<tr>
		<th scope="row"><label for="receiveReason">비고</label></th>
		<td class="left"><input type="text" id="receiveReason" name="reason" title="입고 비고 입력" size="50" maxlength="500"/></td>
	</tr>
	</tbody>
	</table>
	<div class="btn"><input type="submit" class="s_submit" value="입고" title="입고 버튼"/></div>
	</form>

	<h2 style="clear:both; margin-top:20px;">조정</h2>
	<form action="<c:url value='/asset/item/adjust.do'/>" method="post">
	<input type="hidden" name="${_csrf.parameterName}" value="${_csrf.token}"/>
	<input type="hidden" name="itemCode" value="<c:out value='${item.itemCode}'/>"/>
	<table class="wTable">
	<caption>조정</caption>
	<colgroup><col style="width:20%;"><col></colgroup>
	<tbody>
	<tr>
		<th scope="row"><label for="adjustDelta">증감 수량 <span class="pilsu">*</span></label></th>
		<td class="left"><input type="number" id="adjustDelta" name="delta" title="증감 수량 입력(줄이면 음수)" step="1" required="required"/> 줄일 때는 음수로 입력합니다. 조정 후 재고가 0 미만이면 처리되지 않습니다.</td>
	</tr>
	<tr>
		<th scope="row"><label for="adjustReason">사유 <span class="pilsu">*</span></label></th>
		<td class="left"><input type="text" id="adjustReason" name="reason" title="조정 사유 입력" size="50" maxlength="500" required="required"/></td>
	</tr>
	</tbody>
	</table>
	<div class="btn"><input type="submit" class="s_submit" value="조정" title="조정 버튼"/></div>
	</form>
	</c:if>
	<c:if test="${item.itemTy == 'UNIT'}">
	<p style="clear:both; margin-top:20px;">개별 자산은 자산번호별로 등록하면 입고(+1)가 기록됩니다.
		<a href="<c:url value='/asset/unit/list.do'/>">개별 자산 화면으로 이동</a></p>
	</c:if>
	</c:if>
</div>

</body>
</html>
